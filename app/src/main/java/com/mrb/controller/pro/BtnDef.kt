package com.mrb.controller.pro

data class BtnDef(
    val id: String,
    val label: String,
    val iconRes: Int,
    val pressColor: Int,
    val byte1bit: Int = -1,
    val byte2bit: Int = -1,
    val isGas: Boolean = false,
    val isBrake: Boolean = false
)

data class PlacedBtn(
    val id: String,
    var x: Float,
    var y: Float,
    var w: Int,
    var h: Int
)

data class CustomBtn(
    val id: String,
    val label: String,
    val iconRes: Int,
    val pressColor: Int,
    val byte1bit: Int = -1,
    val byte2bit: Int = -1
)

data class PlacedCustomBtn(
    val id: String,
    var x: Float,
    var y: Float,
    var w: Int = 80,
    var h: Int = 100
)
