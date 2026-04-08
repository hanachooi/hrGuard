package dev.businesstrip.repository;

import dev.businesstrip.constant.BusinessTripStatus;
import dev.businesstrip.entity.BusinessTrip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BusinessTripRepository extends JpaRepository<BusinessTrip, Long> {

    List<BusinessTrip> findByMemberIdOrderByCreatedAtDesc(Long memberId);

    List<BusinessTrip> findByStatusOrderByCreatedAtDesc(BusinessTripStatus status);
}
