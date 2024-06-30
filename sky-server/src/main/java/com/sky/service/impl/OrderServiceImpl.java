package com.sky.service.impl;

import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.entity.AddressBook;
import com.sky.entity.OrderDetail;
import com.sky.entity.Orders;
import com.sky.entity.ShoppingCart;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.mapper.AddressBookMapper;
import com.sky.mapper.OrderDetailMapper;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.ShoppingCartMapper;
import com.sky.service.OrderService;
import com.sky.vo.*;
import com.sky.websocket.WebSocketServer;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.*;
import com.sky.entity.*;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.mapper.*;
import com.sky.result.PageResult;
import com.sky.service.OrderService;
import com.sky.utils.WeChatPayUtil;
import com.sky.vo.OrderSubmitVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
@Service
@Slf4j
public class OrderServiceImpl implements OrderService {
    @Autowired
    private UserMapper userMapper;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderDetailMapper orderDetailMapper;

    @Autowired
    private AddressBookMapper addressBookMapper;

    @Autowired
    private ShoppingCartMapper shoppingCartMapper;

    @Autowired
    private WeChatPayUtil weChatPayUtil;


    @Override
    @Transactional
    public OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO) {
        //处理业务异常（地址簿为空 购物车数据为空）
        AddressBook addressBook = addressBookMapper.getById(ordersSubmitDTO.getAddressBookId());
        if (addressBook == null) {
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }
        Long userId = BaseContext.getCurrentId();
        ShoppingCart shoppingCart = new ShoppingCart();
        shoppingCart.setUserId(userId);
        List<ShoppingCart> shoppingCartList = shoppingCartMapper.list(shoppingCart);
        if (shoppingCartList == null || shoppingCartList.size() == 0) {
            throw new ShoppingCartBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }
        //向订单表插入一条数据
        Orders orders = new Orders();
        BeanUtils.copyProperties(ordersSubmitDTO, orders);
        orders.setOrderTime(LocalDateTime.now());
        orders.setPayStatus(Orders.UN_PAID);
        orders.setStatus(Orders.PENDING_PAYMENT);
        orders.setNumber(String.valueOf(System.currentTimeMillis()));
        orders.setAddress(addressBook.getDetail());
        orders.setPhone(addressBook.getPhone());
        orders.setConsignee(addressBook.getConsignee());
        orders.setUserId(userId);

        orderMapper.insert(orders);
        //向订单明细表插入n条数据
        List<OrderDetail> orderDetailList = new ArrayList<>();
        for (ShoppingCart cart : shoppingCartList){
            OrderDetail orderDetail = new OrderDetail();
            BeanUtils.copyProperties(cart, orderDetail);
            orderDetail.setOrderId(orders.getId());
            orderDetailList.add(orderDetail);
        }
        orderDetailMapper.insertBatch(orderDetailList);
        //清除当前用户的购物车数据
        shoppingCartMapper.deleteByUserId(userId);
        //封装返回数据
        OrderSubmitVO orderSubmitVO = OrderSubmitVO.builder().id(orders.getId())
                .orderTime(orders.getOrderTime())
                .orderNumber(orders.getNumber())
                .orderAmount(orders.getAmount())
                .build();
        return orderSubmitVO;
    }

    /**
     * 订单支付
     *
     * @param ordersPaymentDTO
     * @return
     */
    public OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception {
        // 当前登录用户id
        Long userId = BaseContext.getCurrentId();
        User user = userMapper.getById(userId);

        //调用微信支付接口，生成预支付交易单
//        JSONObject jsonObject = weChatPayUtil.pay(
//                ordersPaymentDTO.getOrderNumber(), //商户订单号
//                new BigDecimal(0.01), //支付金额，单位 元
//                "苍穹外卖订单", //商品描述
//                user.getOpenid() //微信用户的openid
//        );
        JSONObject jsonObject = new JSONObject();

        if (jsonObject.getString("code") != null && jsonObject.getString("code").equals("ORDERPAID")) {
            throw new OrderBusinessException("该订单已支付");
        }

        OrderPaymentVO vo = jsonObject.toJavaObject(OrderPaymentVO.class);
        vo.setPackageStr(jsonObject.getString("package"));

        return vo;
    }

    /**
     * 支付成功，修改订单状态
     *
     * @param outTradeNo
     */
    public void paySuccess(String outTradeNo) {

        // 根据订单号查询订单
        Orders ordersDB = orderMapper.getByNumber(outTradeNo);

        // 根据订单id更新订单的状态、支付方式、支付状态、结账时间
        Orders orders = Orders.builder()
                .id(ordersDB.getId())
                .status(Orders.TO_BE_CONFIRMED)
                .payStatus(Orders.PAID)
                .checkoutTime(LocalDateTime.now())
                .build();

        orderMapper.update(orders);

    }

    @Override
    public PageResult pageQuery(OrdersPageQueryDTO ordersPageQueryDTO) {
        PageHelper.startPage(ordersPageQueryDTO.getPage(), ordersPageQueryDTO.getPageSize());
        Long userId = BaseContext.getCurrentId();
        ordersPageQueryDTO.setUserId(userId);
        Page<OrderVO> page = orderMapper.pageQuery(ordersPageQueryDTO);
        List<OrderVO> orderlist = page.getResult();
        if (orderlist != null && orderlist.size() > 0) {
            orderlist.forEach(
                    order -> {
                        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(order.getId());
                        order.setOrderDetailList(orderDetailList);
                    }
            );
        }
        return new PageResult(page.getTotal(), orderlist);
    }

    @Override
    public OrderVO getOrderByIdWithUsername(Integer id) {
        Orders order = orderMapper.getById(id);
        OrderVO orderVO = new OrderVO();
        BeanUtils.copyProperties(order, orderVO);
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(order.getId());
        orderVO.setOrderDetailList(orderDetailList);
        return orderVO;
    }

    @Override
    public void cancel(Integer id) {
        Orders order = orderMapper.getById(id);
        Integer status = order.getStatus();
        if (status > 2) {
            throw new OrderBusinessException("订单不可取消");
        }

        if (status == Orders.TO_BE_CONFIRMED) {
            log.info("已退款");
            order.setPayStatus(Orders.REFUND);
        }
        order.setStatus(Orders.CANCELLED);
        order.setCancelReason("用户取消");
        order.setCancelTime(LocalDateTime.now());
        orderMapper.update(order);
    }

    @Override
    public void repetition(Long id) {
        Long userId = BaseContext.getCurrentId();
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(id);
        List<ShoppingCart> shoppingCartList = new ArrayList<>();
        orderDetailList.forEach(
                orderDetail -> {
                    ShoppingCart shoppingCart = new ShoppingCart();
                    BeanUtils.copyProperties(orderDetail, shoppingCart, "id");
                    shoppingCart.setCreateTime(LocalDateTime.now());
                    shoppingCart.setUserId(userId);
                    shoppingCartList.add(shoppingCart);
                }
        );
        shoppingCartMapper.insertBatch(shoppingCartList);
    }

    @Override
    public PageResult conditionSearch(OrdersPageQueryDTO ordersPageQueryDTO) {
        PageHelper.startPage(ordersPageQueryDTO.getPage(), ordersPageQueryDTO.getPageSize());
//        Long userId = BaseContext.getCurrentId();
//        ordersPageQueryDTO.setUserId(userId);
        Page<OrderVO> page = orderMapper.pageQuery(ordersPageQueryDTO);
        List<OrderVO> orderList = page.getResult();
        orderList.forEach(
                order -> {
                    String orderDishes = getOrderDishes(order);
                    order.setOrderDishes(orderDishes);
                }
        );
        return new PageResult(page.getTotal(), orderList);
    }

    @Override
    public OrderStatisticsVO numberStatistic() {
        Integer toBeConfirmed = orderMapper.countStatus(Orders.TO_BE_CONFIRMED);
        Integer confirmed = orderMapper.countStatus(Orders.CONFIRMED);
        Integer deliveryInProgress = orderMapper.countStatus(Orders.DELIVERY_IN_PROGRESS);

        OrderStatisticsVO orderStatisticsVO = new OrderStatisticsVO();
        orderStatisticsVO.setToBeConfirmed(toBeConfirmed);
        orderStatisticsVO.setConfirmed(confirmed);
        orderStatisticsVO.setDeliveryInProgress(deliveryInProgress);
        return orderStatisticsVO;
    }

    @Override
    public void confirm(Long id) {
        Orders order = Orders.builder().id(id)
                .status(Orders.CONFIRMED)
                .build();
        orderMapper.update(order);
    }

    @Override
    public void rejection(OrdersRejectionDTO ordersRejectionDTO) {
        Orders order = orderMapper.getById(ordersRejectionDTO.getId());
        Integer status = order.getStatus();
        Integer payStatus = order.getPayStatus();
        if (!status.equals(Orders.TO_BE_CONFIRMED)|| order == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        if (payStatus.equals(Orders.PAID)){
            log.info("已退款");
        }else {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        order = Orders.builder().id(new Long(ordersRejectionDTO.getId()))
                                .status(Orders.CANCELLED)
                                .rejectionReason(ordersRejectionDTO.getRejectionReason())
                                .cancelTime(LocalDateTime.now())
                                .payStatus(Orders.REFUND)
                                .build();
        orderMapper.update(order);

    }

    @Override
    public void delivery(Integer id) {
        Orders order = orderMapper.getById(id);
        Integer status = order.getStatus();
        if (!status.equals(Orders.CONFIRMED) || status== null){
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        Orders orderT = Orders.builder()
                .id(new Long(id))
                .status(Orders.DELIVERY_IN_PROGRESS)
                .build();
        orderMapper.update(orderT);
    }

    @Override
    public void cancel(OrdersCancelDTO ordersCancelDTO) {
        Orders order = orderMapper.getById(ordersCancelDTO.getId());
        Integer payStatus = order.getPayStatus();
        if (payStatus.equals(Orders.PAID)){
            log.info("已退款");
        }
        Orders orderT = Orders.builder().id(new Long(ordersCancelDTO.getId()))
                .status(Orders.CANCELLED)
                .cancelTime(LocalDateTime.now())
                .payStatus(Orders.REFUND)
                .cancelReason(ordersCancelDTO.getCancelReason()).build();
        orderMapper.update(orderT);
    }

    @Override
    public void complete(Integer id) {
        Orders order = orderMapper.getById(id);
        Integer status = order.getStatus();
        if (!status.equals(Orders.DELIVERY_IN_PROGRESS)|| order == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        Orders orderT = Orders.builder().id(new Long(id))
                .status(Orders.COMPLETED)
                .deliveryTime(LocalDateTime.now())
                .build();
        orderMapper.update(orderT);
    }


    public String getOrderDishes(OrderVO orderVO){
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orderVO.getId());
        List<String> orderDishes = new ArrayList<>();
        orderDetailList.forEach(
                orderDetail -> {
                    orderDishes.add(orderDetail.getName() + "*" + orderDetail.getAmount() + ";");
                }
        );
        return String.join("",orderDishes);
    }
}
