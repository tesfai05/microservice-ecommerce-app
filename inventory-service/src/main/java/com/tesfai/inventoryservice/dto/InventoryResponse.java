package com.tesfai.inventoryservice.dto;

import lombok.Builder;

@Builder
public record InventoryResponse(String skuCode, boolean isInStock) {
}
