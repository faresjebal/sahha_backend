package com.sahha.file.service.medicalfilescanservice;

import com.sahha.file.dto.response.MedicalFileScanResponse;

record AppliedFileScanDecision(
		MedicalFileScanResponse response,
		String storageKey) {
}
