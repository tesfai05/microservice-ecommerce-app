package com.tesfai.orderservice.controller;

import com.tesfai.orderservice.dto.OrderRequest;
import com.tesfai.orderservice.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public String placeOrder(@RequestBody OrderRequest orderRequest) {
        if(orderService.placeOrder(orderRequest)) {
            return "Order Placed Successfully";
        }
        else {
            return "Error in placing the order.";
        }
    }
}
