package com.tesfai.orderservice.service;

import com.tesfai.orderservice.dto.InventoryResponse;
import com.tesfai.orderservice.dto.OrderLineItemsDto;
import com.tesfai.orderservice.dto.OrderRequest;
import com.tesfai.orderservice.entity.Order;
import com.tesfai.orderservice.entity.OrderLineItem;
import com.tesfai.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final WebClient.Builder webClientBuilder;

    public boolean placeOrder(OrderRequest orderRequest) {
        Order order = new Order();
        order.setOrderNumber(UUID.randomUUID().toString());

        List<OrderLineItem> orderLineItems = orderRequest.orderLineItemsDtoList()
                .stream()
                .map(this::mapToDto)
                .toList();

        order.setOrderLineItemsList(orderLineItems);

        List<String> skuCodes = order.getOrderLineItemsList().stream()
                .map(OrderLineItem::getSkuCode)
                .toList();

        // Call Inventory Service, and place order if product is in stock
        InventoryResponse[] inventoryResponseArray = webClientBuilder.build().get()
                .uri("http://inventory-service/api/inventory",
                        uriBuilder -> uriBuilder.queryParam("skuCode", skuCodes).build())
                .retrieve()
                .bodyToMono(InventoryResponse[].class)
                .block(); //synchronous

        boolean allProductsInStock = inventoryResponseArray != null?Arrays.stream(inventoryResponseArray)
                .allMatch(InventoryResponse::isInStock):false;

        if(allProductsInStock){
            orderRepository.save(order);
            return true;
        } else {
            log.error("One or more of the product(s) is/are not in stock, please try again later");
            return false;
        }
    }

    private OrderLineItem mapToDto(OrderLineItemsDto orderLineItemsDto) {
        OrderLineItem orderLineItem = new OrderLineItem();
        orderLineItem.setPrice(orderLineItemsDto.price());
        orderLineItem.setQuantity(orderLineItemsDto.quantity());
        orderLineItem.setSkuCode(orderLineItemsDto.skuCode());
        return orderLineItem;
    }
}
