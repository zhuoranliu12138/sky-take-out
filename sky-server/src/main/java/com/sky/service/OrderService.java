package com.sky.service;

import com.sky.dto.OrdersSubmitDTO;
import com.sky.entity.Orders;
import com.sky.result.PageResult;
import com.sky.vo.OrderSubmitVO;

import com.sky.dto.*;
import com.sky.vo.*;
import io.swagger.models.auth.In;

public interface OrderService {
    OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO);

    /**
     * 订单支付
     * @param ordersPaymentDTO
     * @return
     */
    OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception;

    /**
     * 支付成功，修改订单状态
     * @param outTradeNo
     */
    void paySuccess(String outTradeNo);

    PageResult pageQuery(OrdersPageQueryDTO ordersPageQueryDTO);

    OrderVO getOrderByIdWithUsername(Integer id);


    void cancel(Integer id);

    void repetition(Long id);

    PageResult conditionSearch(OrdersPageQueryDTO ordersPageQueryDTO);

    OrderStatisticsVO numberStatistic();

    void confirm(Long id);


    void rejection(OrdersRejectionDTO ordersRejectionDTO);

    void delivery(Integer id);

    void cancel(OrdersCancelDTO ordersCancelDTO);

    void complete(Integer id);
}
