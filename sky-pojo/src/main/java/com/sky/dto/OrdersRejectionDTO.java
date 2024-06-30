package com.sky.dto;

import io.swagger.models.auth.In;
import lombok.Data;

import java.io.Serializable;

@Data
public class OrdersRejectionDTO implements Serializable {

    private Integer id;

    //订单拒绝原因
    private String rejectionReason;

}
