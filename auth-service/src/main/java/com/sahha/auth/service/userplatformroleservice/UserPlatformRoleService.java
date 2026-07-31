package com.sahha.auth.service.userplatformroleservice;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sahha.auth.repository.UserPlatformRoleRepository;

@Service
public class UserPlatformRoleService {

	private final UserPlatformRoleRepository assignmentRepository;

	public UserPlatformRoleService(
			UserPlatformRoleRepository assignmentRepository) {
		this.assignmentRepository = assignmentRepository;
	}

	@Transactional(readOnly = true)
	public List<String> findActiveRoleCodes(UUID userId) {
		UUID requiredUserId = Objects.requireNonNull(
				userId,
				"userId must not be null");
		return assignmentRepository
				.findAllActiveWithRoleByUserId(requiredUserId)
				.stream()
				.map(assignment -> assignment.getRole().getCode().name())
				.sorted()
				.toList();
	}
}
