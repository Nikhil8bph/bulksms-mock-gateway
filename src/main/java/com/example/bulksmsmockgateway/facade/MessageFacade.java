package com.example.bulksmsmockgateway.facade;

import com.example.bulksmsmockgateway.dtos.common.GatewayData;
import com.example.bulksmsmockgateway.mapper.PersistenceMapper;
import com.example.bulksmsmockgateway.repo.ContactRepository;
import com.example.bulksmsmockgateway.repo.GroupMemberRepository;
import com.example.bulksmsmockgateway.repo.MessageRepository;
import com.example.bulksmsmockgateway.repo.MessageSpecifications;
import com.example.bulksmsmockgateway.repo.SubmissionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Component
public class MessageFacade {
    private final MessageRepository messages;
    private final SubmissionRepository submissions;
    private final ContactRepository contacts;
    private final GroupMemberRepository groupMembers;
    private final PersistenceMapper mapper;

    public MessageFacade(MessageRepository messages, SubmissionRepository submissions, ContactRepository contacts,
                         GroupMemberRepository groupMembers, PersistenceMapper mapper) {
        this.messages = messages;
        this.submissions = submissions;
        this.contacts = contacts;
        this.groupMembers = groupMembers;
        this.mapper = mapper;
    }

    public List<GatewayData.Message> list(GatewayData.MessageQuery query) {
        Sort sort = Sort.by(query.ascending() ? Sort.Direction.ASC : Sort.Direction.DESC, "id");
        return messages.findBy(MessageSpecifications.matching(query),
                results -> results.sortBy(sort).limit(query.limit()).all()).stream().map(mapper::message).toList();
    }

    public Optional<GatewayData.Message> find(long id, Instant now) {
        return messages.findById(id).filter(message -> !message.availableAt.isAfter(now)).map(mapper::message);
    }

    public List<GatewayData.Message> related(long id) {
        return messages.findByRelatedSentMessageIdAndDirectionOrderById(id, "RECEIVED")
                .stream().map(mapper::message).toList();
    }

    public Optional<GatewayData.Message> replyTarget(String destination, String origin, Instant now) {
        return messages.findFirstByDestinationAndOriginAndDirectionAndRepliableTrueAndStatusTypeAndAvailableAtLessThanEqualOrderByIdDesc(
                destination, origin, "SENT", "DELIVERED", now).map(mapper::message);
    }

    public long sentSince(Instant since) {
        return messages.countByDirectionAndSubmittedAtGreaterThanEqual("SENT", since);
    }

    public Optional<List<GatewayData.Message>> duplicate(int key, Instant cutoff) {
        return submissions.findFirstByDeduplicationIdAndSubmittedAtGreaterThanEqualOrderByIdDesc(key, cutoff)
                .map(submission -> messages.findBySubmissionIdOrderById(submission.id).stream().map(mapper::message).toList());
    }

    public GatewayData.Submission createSubmission(GatewayData.Submission data, Integer key) {
        var entity = mapper.submission(data);
        entity.deduplicationId = key;
        return mapper.submission(submissions.save(entity));
    }

    public GatewayData.Message create(GatewayData.Message data, Instant mailDue) {
        var entity = mapper.message(data);
        entity.mailDue = mailDue;
        return mapper.message(messages.save(entity));
    }

    public Optional<GatewayData.MailJob> claimMail(Instant now) {
        return messages.findByMailDueLessThanEqualOrderByMailDueAscIdAsc(now, PageRequest.ofSize(1))
                .stream().findFirst().map(entity -> {
                    entity.mailLease = UUID.randomUUID().toString();
                    entity.mailDue = now.plus(Duration.ofMinutes(5));
                    return new GatewayData.MailJob(mapper.message(entity), entity.mailLease, ++entity.mailAttempts);
                });
    }

    public Optional<GatewayData.Message> finishMail(GatewayData.MailJob job, String error, String failureSubtype, Instant retryAt) {
        return messages.findById(job.message().id())
                .filter(entity -> Objects.equals(entity.mailLease, job.lease()))
                .flatMap(entity -> {
                    entity.mailError = error;
                    entity.mailLease = null;
                    entity.mailDue = retryAt;
                    if (retryAt != null) {
                        return Optional.empty();
                    }
                    entity.statusType = failureSubtype == null ? "DELIVERED" : "FAILED";
                    entity.statusSubtype = failureSubtype;
                    return Optional.of(mapper.message(entity));
                });
    }

    public Optional<GatewayData.Contact> contact(String phone) {
        return contacts.findByPhoneNumber(phone).map(mapper::contact);
    }

    public List<String> group(String id, String name) {
        var members = id == null ? groupMembers.findByGroupNameOrderById(name) : groupMembers.findByGroupIdOrderById(id);
        return members.stream().map(member -> member.phoneNumber).toList();
    }
}
