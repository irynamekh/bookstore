package com.bookstore.service.order;

import com.bookstore.dto.order.CreateOrderRequestDto;
import com.bookstore.dto.order.OrderResponseDto;
import com.bookstore.dto.order.UpdateStatusRequestDto;
import com.bookstore.dto.orderitem.OrderItemResponseDto;
import com.bookstore.exception.EntityNotFoundException;
import com.bookstore.mapper.OrderItemMapper;
import com.bookstore.mapper.OrderMapper;
import com.bookstore.model.Order;
import com.bookstore.model.OrderItem;
import com.bookstore.model.ShoppingCart;
import com.bookstore.model.User;
import com.bookstore.repository.OrderItemRepository;
import com.bookstore.repository.OrderRepository;
import com.bookstore.security.AuthentificationService;
import com.bookstore.service.cart.ShoppingCartService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ShoppingCartService shoppingCartService;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final AuthentificationService authentificationService;

    @Transactional
    @Override
    public List<OrderResponseDto> getAll(Pageable pageable) {
        return orderRepository.findAllByUserId(authentificationService.getUserId()).stream()
                .map(orderMapper::toDto)
                .toList();
    }

    @Transactional
    @Override
    public OrderResponseDto save(CreateOrderRequestDto requestDto) {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        ShoppingCart cart = shoppingCartService.getById(user.getId());
        Order order = new Order();
        order.setUser(user);
        order.setShippingAddress(requestDto.getShippingAddress());
        order.setOrderDate(LocalDateTime.now());
        order.setTotal(cart.getCartItems().stream()
                .map(c -> c.getBook().getPrice().multiply(BigDecimal.valueOf(c.getQuantity())))
                .reduce(BigDecimal.valueOf(0), BigDecimal::add));
        orderRepository.save(order);
        OrderResponseDto dto = orderMapper.toDto(order);
        dto.setOrderItems(getOrderItemDtos(getOrderItems(cart, order)));
        return dto;
    }

    @Transactional
    @Override
    public OrderResponseDto updateOrderStatus(Long id, UpdateStatusRequestDto requestDto) {
        Order order = orderRepository.findById(id).orElseThrow(() -> new EntityNotFoundException(
                "Can't find order by id: " + id));
        order.setStatus(requestDto.getStatus());
        return orderMapper.toDto(orderRepository.save(order));
    }

    @Transactional
    @Override
    public List<OrderItemResponseDto> getAllByOrderId(Long orderId, Pageable pageable) {
        return getOrderById(orderId).getOrderItems().stream()
                .map(orderItemMapper::toDto)
                .toList();
    }

    @Transactional
    @Override
    public OrderItemResponseDto getOrderItemById(Long orderId, Long itemId) {
        return getOrderById(orderId).getOrderItems().stream()
                .filter(c -> Objects.equals(c.getId(), itemId))
                .map(orderItemMapper::toDto)
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException(
                        "Can't find order item by id: " + itemId));
    }

    private Order getOrderById(Long id) {
        return orderRepository.findAllByUserId(authentificationService.getUserId()).stream()
                .filter(o -> Objects.equals(o.getId(), id))
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException(
                        "Can't find order by id: " + id));
    }

    private Set<OrderItem> getOrderItems(ShoppingCart cart, Order order) {
        return cart.getCartItems().stream()
                .map(orderItemMapper::mapCartItemToOrderItem)
                .peek(oi -> oi.setPrice(oi.getPrice().multiply(
                        BigDecimal.valueOf(oi.getQuantity()))))
                .peek(oi -> oi.setOrder(order))
                .peek(orderItemRepository::save)
                .collect(Collectors.toSet());
    }

    private Set<OrderItemResponseDto> getOrderItemDtos(Set<OrderItem> orderItems) {
        return orderItems.stream()
                .map(orderItemMapper::toDto)
                .collect(Collectors.toSet());
    }
}
