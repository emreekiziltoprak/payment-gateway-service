package com.emrekiziltoprak.payment.gateway.service.adapters.in.web;

public record PaymentResponseDTO(
        String paymentId,
        String status,
        String message
) {}