package com.glory.inventorysaas.event;

import com.glory.inventorysaas.entity.User;
import com.glory.inventorysaas.entity.enums.NotificationType;
import com.glory.inventorysaas.entity.enums.Role;
import com.glory.inventorysaas.repository.UserRepository;
import com.glory.inventorysaas.security.SecurityUtils;
import com.glory.inventorysaas.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class InAppNotificationListener {
    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final SecurityUtils securityUtils;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleStockTransferRequested(StockTransferRequestedEvent event) {
        List<User> managers = userRepository.findByCompanyIdAndWarehouseIdAndRole(
                event.companyId(), event.sourceWarehouseId(), Role.WAREHOUSE_MANAGER);

        String message = event.requestingWarehouseName()
                + " requested "
                + event.quantity()
                + " units of "
                + event.productName();

        for (User manager : managers) {

            notificationService.sendNotification(
                    event.companyId(),
                    manager.getId(),
                    "New Stock Transfer Request",
                    message,
                    NotificationType.TRANSFER_REQUESTED,
                    event.transferRequestId()
            );
        }

        log.info(
                "Stock transfer request notification processed. Transfer ID: {}, Recipients: {}",
                event.transferRequestId(),
                managers.size()
        );
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleStockTransferResolved(StockTransferResolvedEvent event) {
        List<User> managers = userRepository.findByCompanyIdAndWarehouseIdAndRole(
                event.companyId(), event.requestingWarehouseId(), Role.WAREHOUSE_MANAGER);

        String title = event.accepted()
                ? "Transfer Request Approved"
                : "Transfer Request Declined";

        NotificationType type = event.accepted()
                ? NotificationType.TRANSFER_ACCEPTED
                : NotificationType.TRANSFER_DECLINED;


        String message = event.sourceWarehouseName()
                + (event.accepted() ? " approved " : " declined ")
                + "your request for "
                + event.quantity()
                + " units of "
                + event.productName();

        for (User manager : managers) {

            notificationService.sendNotification(
                    event.companyId(),
                    manager.getId(),
                    title,
                    message,
                    type,
                    event.transferRequestId()
            );
        }

        log.info(
                "Stock transfer resolution notification processed. Transfer ID: {}, Accepted: {}, Recipients: {}",
                event.transferRequestId(),
                event.accepted(),
                managers.size()
        );
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleCustomerOrderPlaced(CustomerOrderPlacedEvent event) {
        List<User> owners = userRepository.findByCompanyIdAndRole(
                event.companyId(), Role.OWNER);

        String message = "New customer order "
                + event.orderId()
                + " has been placed by "
                + event.customerEmail()
                + ". It contains "
                + event.quantity()
                + " item(s).";

        for (User owner : owners) {

            notificationService.sendNotification(
                    event.companyId(),
                    owner.getId(),
                    "New Customer Order",
                    message,
                    NotificationType.NEW_CUSTOMER_ORDER,
                    event.orderId()
            );
        }

        log.info(
                "Customer order placed notifications processed. Order ID: {}",
                event.orderId()
        );
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleCustomerOrderAssigned(CustomerOrderAssignedEvent event) {
        if (event.reassigned()) {
            return;
        }

        List<User> managers = userRepository.findByCompanyIdAndWarehouseIdAndRole(
                event.companyId(), event.warehouseId(), Role.WAREHOUSE_MANAGER);

        String message = "Order "
                + event.orderId()
                + " from "
                + event.customerEmail()
                + " has been assigned to "
                + event.warehouseName();

        for (User manager : managers) {
            notificationService.sendNotification(
                    event.companyId(),
                    manager.getId(),
                    "New Order Assigned",
                    message,
                    NotificationType.ORDER_ASSIGNED,
                    event.orderId()
            );
        }

        log.info(
                "Customer order assignment notifications processed. Order ID: {}, Warehouse: {}",
                event.orderId(),
                event.warehouseId()
        );
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleCustomerOrderRerouted(CustomerOrderAssignedEvent event) {
        if (!event.reassigned()) {
            return;
        }

        List<User> newWarehouseManagers =
                userRepository.findByCompanyIdAndWarehouseIdAndRole(
                        event.companyId(),
                        event.warehouseId(),
                        Role.WAREHOUSE_MANAGER
                );

        List<User> previousWarehouseManagers =
                userRepository.findByCompanyIdAndWarehouseIdAndRole(
                        event.companyId(),
                        event.previousWarehouseId(),
                        Role.WAREHOUSE_MANAGER
                );

        String newWarehouseMessage = "Order "
                + event.orderId()
                + " from "
                + event.customerEmail()
                + " has been rerouted to "
                + event.warehouseName();

        String previousWarehouseMessage = "Order "
                + event.orderId()
                + " from "
                + event.customerEmail()
                + " has been rerouted from "
                + event.previousWarehouseName()
                + " to "
                + event.warehouseName();

        for (User manager : newWarehouseManagers) {
            notificationService.sendNotification(
                    event.companyId(),
                    manager.getId(),
                    "Customer Order Rerouted To Your Warehouse",
                    newWarehouseMessage,
                    NotificationType.ORDER_REROUTED,
                    event.orderId()
            );
        }

        for (User manager : previousWarehouseManagers) {
            notificationService.sendNotification(
                    event.companyId(),
                    manager.getId(),
                    "Customer Order Rerouted From Your Warehouse",
                    previousWarehouseMessage,
                    NotificationType.ORDER_REROUTED,
                    event.orderId()
            );
        }

        log.info(
                "Customer order rerouting notifications processed. Order ID: {}, Previous warehouse: {}, New warehouse: {}",
                event.orderId(),
                event.previousWarehouseId(),
                event.warehouseId()
        );
    }
}
