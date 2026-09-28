package com.sahha.communication.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sahha.communication.entity.ReferralShareItem;
import com.sahha.communication.entity.ShareResourceType;

public interface ReferralShareItemRepository extends JpaRepository<ReferralShareItem, UUID> {
	boolean existsByReferralIdAndResourceTypeAndResourceId(
			UUID referralId, ShareResourceType resourceType, UUID resourceId);
	List<ReferralShareItem> findAllByReferralIdOrderByCreatedAtAscIdAsc(UUID referralId);
	List<ReferralShareItem> findAllByReferralIdInOrderByReferralIdAscCreatedAtAscIdAsc(
			Collection<UUID> referralIds);
}
