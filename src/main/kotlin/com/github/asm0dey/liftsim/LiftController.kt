package com.github.asm0dey.liftsim

import com.github.asm0dey.liftsim.DoorsController.closeDoors
import com.github.asm0dey.liftsim.DoorsController.cycleDoorsIfClosed
import com.github.asm0dey.liftsim.DoorsController.doorsOpened
import com.github.asm0dey.liftsim.Where.INSIDE
import com.github.asm0dey.liftsim.Where.OUTSIDE
import com.github.asm0dey.liftsim.model.BuildingAndLiftConfig
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.concurrent.LinkedBlockingDeque
import kotlin.concurrent.thread

object LiftController {
    private val commands = LinkedBlockingDeque<Command>()
    @Volatile private var busy = false
    @Volatile private var currentFloor = 1

    fun launch(conf: BuildingAndLiftConfig) = thread(name = "lift-controller") {
        val cycleDoorsIfClosed = { cycleDoorsIfClosed(conf.openCloseTime) }
        while (true) {
            val (where, targetFloor) = commands.take()!!

            if (busy || targetFloor != currentFloor && doorsOpened() && where == OUTSIDE) {
                println("Elevator is busy. Please, try again later.")
                continue
            }

            if (targetFloor > conf.floors!! || targetFloor < 1) {
                println("Elevator is called from nonexistent floor. Ignoring")
                continue
            }

            if (currentFloor == targetFloor) {
                cycleDoorsIfClosed()
                continue
            }

            busy = true

            //we launch it in separate thread to be able to handle commands as soon as possible,
            // not waiting for lift finish its actions
            thread(name = "lift-mover") {
                if (where == INSIDE && doorsOpened())
                    closeDoors()

                val timePerFloor = conf.floorHeight.divide(conf.speed, 3, RoundingMode.HALF_EVEN)
                val floorsToVisit =
                        if (currentFloor < targetFloor) currentFloor + 1..targetFloor
                        else currentFloor - 1 downTo targetFloor
                floorsToVisit.forEach {
                    val millisPerFloor = (timePerFloor * BigDecimal("1000")).toLong()
                    Thread.sleep(millisPerFloor)
                    println("On floor $it")
                }
                cycleDoorsIfClosed()
                currentFloor = targetFloor
                busy = false
            }
        }
    }

    fun invoke(c: Command) = commands.add(c)
}