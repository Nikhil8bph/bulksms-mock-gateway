package com.example.bulksmsmockgateway.service;

import com.example.bulksmsmockgateway.dtos.common.GatewayData;
import com.example.bulksmsmockgateway.dtos.request.Requests;
import com.example.bulksmsmockgateway.exception.ApiException;
import com.example.bulksmsmockgateway.facade.MessageFacade;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.example.bulksmsmockgateway.validation.ApiInput.*;

@Component
class MessageComposer {
    private static final String GSM = "@£$¥èéùìòÇ\nØø\rÅåΔ_ΦΓΛΩΠΨΣΘΞÆæßÉ !\"#¤%&'()*+,-./0123456789:;<=>?¡"
            + "ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§¿abcdefghijklmnopqrstuvwxyzäöñüà";
    private static final String GSM_EXT = "\f^{}\\[~]|€";
    private static final Pattern TEMPLATE = Pattern.compile("\\{(F[0-9]+|fn|sn)#+}");
    private static final Pattern CONTACT_TEMPLATE = Pattern.compile("\\{(?:fn|sn)#+}");
    private static final Map<String, Integer> PROTOCOLS = Map.ofEntries(
            Map.entry("IMPLICIT", 0), Map.entry("SHORT_MESSAGE_TYPE_0", 64),
            Map.entry("REPLACE_MESSAGE_1", 65), Map.entry("REPLACE_MESSAGE_2", 66), Map.entry("REPLACE_MESSAGE_3", 67),
            Map.entry("REPLACE_MESSAGE_4", 68), Map.entry("REPLACE_MESSAGE_5", 69), Map.entry("REPLACE_MESSAGE_6", 70),
            Map.entry("REPLACE_MESSAGE_7", 71), Map.entry("RETURN_CALL", 95), Map.entry("ME_DOWNLOAD", 125),
            Map.entry("ME_DEPERSONALIZE", 126), Map.entry("SIM_DOWNLOAD", 127));
    private static final Map<String, Integer> CLASSES = Map.of(
            "FLASH_SMS", 0, "ME_SPECIFIC", 1, "SIM_SPECIFIC", 2, "TE_SPECIFIC", 3);
    static final int MAX_BATCH = 50_000;

    private final MessageFacade messages;

    MessageComposer(MessageFacade messages) {
        this.messages = messages;
    }

    List<PreparedMessage> compose(List<Requests.Submission> entries, boolean autoUnicode) {
        if (entries.isEmpty()) {
            throw ApiException.invalid("At least one submission is required");
        }
        if (entries.size() > MAX_BATCH) {
            throw ApiException.batchTooLarge();
        }
        List<PreparedMessage> result = new ArrayList<>();
        Map<String, GatewayData.Contact> contacts = new HashMap<>();
        for (Requests.Submission entry : entries) {
            if (entry == null) {
                throw ApiException.invalid("Each submission must be an object");
            }
            Sender sender = sender(entry.from());
            String body = required(entry.body(), "body", 65_535);
            String encoding = choice(entry.encoding(), "TEXT", "encoding", Set.of("TEXT", "UNICODE", "BINARY"));
            String routing = choice(entry.routingGroup(), "STANDARD", "routingGroup", Set.of("STANDARD", "ECONOMY", "PREMIUM"));
            String reports = choice(entry.deliveryReports(), "ALL", "deliveryReports", Set.of("ALL", "ERRORS", "NONE"));
            String userId = bounded(entry.userSuppliedId(), "userSuppliedId", 20);
            int protocol = enumNumber(entry.protocolId(), PROTOCOLS, 0, "protocolId");
            int messageClass = enumNumber(entry.messageClass(), CLASSES, 2, "messageClass");
            int maxParts = entry.longMessageMaxParts() == null ? 3 : entry.longMessageMaxParts();
            if (maxParts < 1 || maxParts > 255) {
                throw ApiException.invalid("longMessageMaxParts must be between 1 and 255");
            }
            boolean needsContact = CONTACT_TEMPLATE.matcher(body).find();
            for (Recipient recipient : recipients(entry.to())) {
                if (result.size() == MAX_BATCH) {
                    throw ApiException.batchTooLarge();
                }
                GatewayData.Contact contact = needsContact
                        ? contacts.computeIfAbsent(recipient.phone(), phone -> messages.contact(phone)
                                .orElse(new GatewayData.Contact(phone, "", "")))
                        : null;
                String rendered = render(body, recipient.fields(), contact);
                Encoded content = encode(rendered, encoding, autoUnicode);
                if (content.parts() > maxParts) {
                    throw ApiException.invalid("Message exceeds longMessageMaxParts");
                }
                result.add(new PreparedMessage(sender, recipient.phone(), content, routing, protocol, messageClass, reports, userId));
            }
        }
        return result;
    }

    private List<Recipient> recipients(JsonNode to) {
        if (to == null || to.isNull() || (to.isArray() && to.isEmpty())) {
            throw ApiException.invalid("At least one recipient is required");
        }
        List<Recipient> result = new ArrayList<>();
        if (to.isArray()) {
            boolean structured = to.get(0).isObject();
            for (JsonNode node : to) {
                if (structured != node.isObject()) {
                    throw ApiException.invalid("Recipient arrays must contain only phone numbers or only recipient objects");
                }
                addRecipient(result, node);
                if (result.size() > MAX_BATCH) {
                    throw ApiException.batchTooLarge();
                }
            }
        } else {
            addRecipient(result, to);
        }
        return result;
    }

    private void addRecipient(List<Recipient> result, JsonNode node) {
        if (node.isTextual()) {
            result.add(new Recipient(phone(node.asString()), List.of()));
            return;
        }
        if (!node.isObject()) {
            throw ApiException.invalid("Expected a recipient phone number or object");
        }
        String type = choice(text(node.get("type"), "to.type"), "INTERNATIONAL", "to.type", Set.of("INTERNATIONAL", "GROUP"));
        if (type.equals("GROUP")) {
            String groupId = bounded(text(node.get("id"), "to.id"), "to.id", 100);
            String groupName = bounded(text(node.get("name"), "to.name"), "to.name", 100);
            if ((groupId == null || groupId.isBlank()) && (groupName == null || groupName.isBlank())) {
                throw ApiException.invalid("A GROUP recipient needs an id or name");
            }
            var phones = messages.group(groupId == null || groupId.isBlank() ? null : groupId, groupName);
            if (phones.isEmpty()) {
                throw ApiException.invalidGroup();
            }
            phones.forEach(phone -> result.add(new Recipient(phone(phone), List.of())));
            return;
        }
        JsonNode fields = node.get("fields");
        List<String> values = new ArrayList<>();
        if (fields != null && !fields.isNull()) {
            if (!fields.isArray()) {
                throw ApiException.invalid("to.fields must be an array of strings");
            }
            for (JsonNode field : fields) {
                if (!field.isTextual()) {
                    throw ApiException.invalid("to.fields must contain strings");
                }
                values.add(field.asString());
            }
        }
        result.add(new Recipient(phone(text(node.get("address"), "to.address")), values));
    }

    private static Sender sender(JsonNode from) {
        if (from == null || from.isNull()) {
            return new Sender("BulkSMS", false);
        }
        String type;
        String address;
        if (from.isTextual()) {
            address = from.asString();
            type = address.startsWith("+") || address.matches("[0-9]+") ? "INTERNATIONAL" : "ALPHANUMERIC";
        } else if (from.isObject()) {
            type = required(text(from.get("type"), "from.type"), "from.type", 20);
            address = text(from.get("address"), "from.address");
        } else {
            throw ApiException.invalid("Expected a sender string or object");
        }
        return switch (type) {
            case "REPLIABLE" -> {
                if (address != null) {
                    throw ApiException.invalid("REPLIABLE does not accept from.address");
                }
                yield new Sender("+447700900000", true);
            }
            case "INTERNATIONAL" -> {
                phone(address);
                yield new Sender(address, false);
            }
            case "ALPHANUMERIC" -> new Sender(required(address, "from.address", 11), false);
            case "SHORTCODE" -> {
                if (address == null || !address.matches("[0-9]{1,6}")) {
                    throw ApiException.invalid("SHORTCODE requires 1 to 6 digits");
                }
                yield new Sender(address, false);
            }
            default -> throw ApiException.invalid("Invalid from.type");
        };
    }

    private static int enumNumber(JsonNode node, Map<String, Integer> values, int fallback, String field) {
        if (node == null || node.isNull()) {
            return fallback;
        }
        if (node.isIntegralNumber() && node.canConvertToInt() && values.containsValue(node.asInt())) {
            return node.asInt();
        }
        if (node.isTextual() && values.containsKey(node.asString())) {
            return values.get(node.asString());
        }
        throw ApiException.invalid("Invalid " + field);
    }

    private static String render(String body, List<String> fields, GatewayData.Contact contact) {
        Matcher matcher = TEMPLATE.matcher(body);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String marker = matcher.group(1);
            String value = switch (marker) {
                case "fn" -> contact == null ? null : contact.firstName();
                case "sn" -> contact == null ? null : contact.surname();
                default -> field(fields, marker);
            };
            int length = matcher.end() - matcher.start();
            if (value != null && value.codePointCount(0, value.length()) > length) {
                value = value.substring(0, value.offsetByCodePoints(0, length));
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(value == null ? "" : value));
        }
        return matcher.appendTail(result).toString();
    }

    private static String field(List<String> fields, String marker) {
        try {
            int index = Integer.parseInt(marker.substring(1));
            return index < fields.size() ? fields.get(index) : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    static Encoded encode(String body, String encoding, boolean autoUnicode) {
        if (encoding.equals("BINARY")) {
            if (body.length() % 2 != 0 || !body.matches("[0-9a-fA-F]+")) {
                throw ApiException.invalid("BINARY body must contain complete hexadecimal byte pairs");
            }
            int bytes = body.length() / 2;
            return new Encoded(body, encoding, bytes <= 140 ? 1 : (bytes + 133) / 134);
        }
        boolean unicode = encoding.equals("UNICODE") || (autoUnicode && body.codePoints().anyMatch(ch -> gsmUnits(ch) == 0));
        if (unicode) {
            return new Encoded(body, "UNICODE", parts(body, 70, 67, Character::charCount));
        }
        StringBuilder converted = new StringBuilder();
        body.codePoints().forEach(ch -> converted.appendCodePoint(gsmUnits(ch) == 0 ? '?' : ch));
        String text = converted.toString();
        return new Encoded(text, "TEXT", parts(text, 160, 153, MessageComposer::gsmUnits));
    }

    private static int parts(String body, int single, int continued, java.util.function.IntUnaryOperator units) {
        if (body.codePoints().map(units).sum() <= single) {
            return 1;
        }
        int count = 1;
        int used = 0;
        for (int offset = 0; offset < body.length();) {
            int codePoint = body.codePointAt(offset);
            int size = units.applyAsInt(codePoint);
            if (used + size > continued) {
                count++;
                used = 0;
            }
            used += size;
            offset += Character.charCount(codePoint);
        }
        return count;
    }

    private static int gsmUnits(int codePoint) {
        if (GSM.indexOf(codePoint) >= 0) {
            return 1;
        }
        return GSM_EXT.indexOf(codePoint) >= 0 ? 2 : 0;
    }

    record Sender(String address, boolean repliable) {}
    record Encoded(String body, String encoding, int parts) {}
    private record Recipient(String phone, List<String> fields) {}
    record PreparedMessage(Sender sender, String phone, Encoded content, String routing, int protocolId,
                           int messageClass, String deliveryReports, String userSuppliedId) {}
}
