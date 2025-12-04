package com.dearjolly.server.domain.skeleton.controller;

import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

import com.dearjolly.server.domain.skeleton.dto.request.SkeletonCreateRequest;
import com.dearjolly.server.domain.skeleton.dto.response.SkeletonGetResponse;
import com.dearjolly.server.domain.skeleton.dto.response.SkeletonListResponse;
import com.dearjolly.server.domain.skeleton.service.SkeletonService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/skeleton")
@RequiredArgsConstructor
public class SkeletonController {

    private final SkeletonService skeletonService;

    @GetMapping
    public ResponseEntity<SkeletonListResponse> getSkeletons() {
        return ResponseEntity
                .status(OK)
                .body(skeletonService.getSkeletons());
    }

    @PostMapping
    public ResponseEntity<SkeletonGetResponse> createSkeleton(
            @Valid @RequestBody SkeletonCreateRequest request
    ) {
        return ResponseEntity
                .status(CREATED)
                .body(skeletonService.createSkeleton(request));
    }
}
