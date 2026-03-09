package com.tesfai.orderservice.dto;

public record InventoryResponse (
     String skuCode,
     boolean isInStock
){}
