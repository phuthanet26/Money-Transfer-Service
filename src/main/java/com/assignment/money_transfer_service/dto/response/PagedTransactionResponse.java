package com.assignment.money_transfer_service.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PagedTransactionResponse {
    private Long accountId;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private List<TransactionResponse> items;
}
