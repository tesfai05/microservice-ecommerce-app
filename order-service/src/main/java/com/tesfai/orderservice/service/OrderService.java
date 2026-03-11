package com.tesfai.orderservice.service;

import com.tesfai.orderservice.dto.InventoryResponse;
import com.tesfai.orderservice.dto.OrderLineItemsDto;
import com.tesfai.orderservice.dto.OrderRequest;
import com.tesfai.orderservice.entity.Order;
import com.tesfai.orderservice.entity.OrderLineItem;
import com.tesfai.orderservice.event.OrderPlacedEvent;
import com.tesfai.orderservice.repository.OrderRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class OrderService {
    private final OrderRepository orderRepository;
    private final WebClient.Builder webClientBuilder;
    private final KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate;

    @CircuitBreaker(name = "inventory", fallbackMethod = "fallbackMethod")
    @TimeLimiter(name = "inventory")
    @Retry(name = "inventory")
    public CompletableFuture<String> placeOrder(OrderRequest orderRequest) {
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
            kafkaTemplate.send("notificationTopic", new OrderPlacedEvent(order.getOrderNumber()));
            return CompletableFuture.supplyAsync(()->"Order Placed Successfully");
        } else {
            log.error("One or more of the product(s) is/are not in stock");
            return CompletableFuture.supplyAsync(()->"One or more of the product(s) is/are not in stock");
        }
    }

    private OrderLineItem mapToDto(OrderLineItemsDto orderLineItemsDto) {
        OrderLineItem orderLineItem = new OrderLineItem();
        orderLineItem.setPrice(orderLineItemsDto.price());
        orderLineItem.setQuantity(orderLineItemsDto.quantity());
        orderLineItem.setSkuCode(orderLineItemsDto.skuCode());
        return orderLineItem;
    }

    public CompletableFuture<String> fallbackMethod(OrderRequest orderRequest, RuntimeException exception){
        return CompletableFuture.supplyAsync(()->"Ooops! Something get wrong - Please try it again.");
    }
}
