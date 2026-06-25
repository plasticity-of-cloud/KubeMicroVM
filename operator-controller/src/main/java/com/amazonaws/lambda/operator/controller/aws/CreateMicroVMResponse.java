package com.amazonaws.lambda.operator.controller.aws;

public record CreateMicroVMResponse(
    String vmId,
    String ipAddress,
    String requestId
) {}
