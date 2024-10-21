package fuseCU

import chisel3._
import chisel3.util.Decoupled
import agile.config._

case object WordWidth extends Field[Int](32)
case object SupportXS extends Field[Boolean](true)

class GcdConfig extends Config(
  (site, here, tail) => {
    case WordWidth => 16
    case SupportXS => true
  }
)

class Pe(implicit p: Parameters) extends Module {
  val dataWidth = p(WordWidth)

  val io = IO(new Bundle() {
    val config = Input(Bool()) // true is OS, false is IS
    val actIn = Input(UInt(dataWidth.W))
    val actOut = Input(UInt(dataWidth.W))
    val weightIn = Input(UInt(dataWidth.W))
    val weightOut = Output(UInt(dataWidth.W))
    val psumIn = Input(UInt(dataWidth.W))
    val psumOut = Output(UInt(dataWidth.W))
  })

  val actReg = RegInit(0.U(dataWidth.W))
  val weightReg = RegInit(0.U(dataWidth.W))
  val psumReg = RegInit(0.U(dataWidth.W))

  val actFrom = Mux(io.config, io.actIn, actReg)
  val psumFrom = Mux(io.config, psumReg, io.psumIn)

  actReg := actFrom
  weightReg := io.weightIn

  val mulOut = actReg * weightReg
  val addOut = psumFrom + mulOut
  psumReg := addOut

  io.actOut := actReg
  io.weightOut := weightReg
  io.psumOut := psumReg

}
