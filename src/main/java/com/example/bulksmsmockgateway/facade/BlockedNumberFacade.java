package com.example.bulksmsmockgateway.facade;

import com.example.bulksmsmockgateway.dtos.common.GatewayData;
import com.example.bulksmsmockgateway.mapper.PersistenceMapper;
import com.example.bulksmsmockgateway.repo.BlockedNumberRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class BlockedNumberFacade {
    private final BlockedNumberRepository blocked;
    private final PersistenceMapper mapper;

    public BlockedNumberFacade(BlockedNumberRepository blocked, PersistenceMapper mapper) {
        this.blocked = blocked;
        this.mapper = mapper;
    }

    public List<GatewayData.BlockedNumber> list(long minId, int limit) {
        return blocked.findByIdGreaterThanEqualOrderById(minId, PageRequest.ofSize(limit))
                .stream().map(mapper::blocked).toList();
    }

    public boolean contains(String phone) {
        return blocked.existsByPhoneNumber(phone);
    }

    public Set<String> matching(Collection<String> phones) {
        Set<String> found = new HashSet<>();
        var unique = List.copyOf(new HashSet<>(phones));
        for (int start = 0; start < unique.size(); start += 1000) {
            found.addAll(blocked.findByPhoneNumberIn(unique.subList(start, Math.min(start + 1000, unique.size())))
                    .stream().map(entity -> entity.phoneNumber).collect(Collectors.toSet()));
        }
        return found;
    }

    public void insert(List<GatewayData.BlockedNumber> requests) {
        Set<String> existing = matching(requests.stream().map(GatewayData.BlockedNumber::phoneNumber).toList());
        blocked.saveAll(requests.stream().filter(request -> existing.add(request.phoneNumber())).map(mapper::blocked).toList());
    }
}
