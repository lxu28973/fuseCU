package fuseCU

import chisel3._
import agile.config._

class PeBundle(implicit p: Parameters) extends Bundle {
  val dataWidth = p(WordWidth)
  val supportXS = p(SupportXS)

  val xsConfig = if (supportXS) Some(Input(Bool())) else None // true is OS, false is IS
  val initIn = Input(Bool())
  val initOut = Output(Bool())
  val actIn = Input(SInt(dataWidth.W))
  val actOut = Output(SInt(dataWidth.W))
  val weightIn = Input(SInt(dataWidth.W))
  val weightOut = Output(SInt(dataWidth.W))
  val psumIn = Input(SInt((4 * dataWidth).W))
  val psumOut = Output(SInt((4 * dataWidth).W))
}

class Pe(implicit p: Parameters) extends Module {
  val dataWidth = p(WordWidth)
  val supportXS = p(SupportXS)

  val io = IO(new PeBundle())

  val actReg = RegInit(0.S(dataWidth.W))
  val weightReg = RegInit(0.S(dataWidth.W))
  val psumReg = RegInit(0.S((4 * dataWidth).W))

  val actFrom = if (supportXS) Mux(io.xsConfig.get, io.actIn, actReg) else actReg
  val psumFrom = if (supportXS) Mux(io.xsConfig.get, psumReg, io.psumIn) else io.psumIn

  val initReg = RegNext(io.initIn)
  io.initOut := initReg

  actReg := actFrom
  weightReg := io.weightIn

  val mulOut = actReg * weightReg
  val addOut = psumFrom + mulOut
  psumReg := addOut

  io.actOut := actReg
  io.weightOut := weightReg
  io.psumOut := psumReg

  when(initReg) {
    if (io.xsConfig.isDefined) {
      when(io.xsConfig.get) {
        psumReg := io.psumIn
        actReg := 0.S
        weightReg := 0.S
      }.otherwise {
        psumReg := 0.S
        actReg := io.actIn
        weightReg := 0.S
      }
    } else {
      psumReg := 0.S
      actReg := io.actIn
      weightReg := 0.S
    }
  }

}

object PeGen extends App {

  import chisel3.stage.{ChiselStage, ChiselGeneratorAnnotation}

  // use "--help" to see more options
  val chiselArgs = Array("-X", "verilog", "-td", "verilog_gen_dir",
    "--emission-options", "disableMemRandomization,disableRegisterRandomization")
  (new chisel3.stage.ChiselStage).execute(
    chiselArgs, Seq(ChiselGeneratorAnnotation(() => new Pe()(new XsPeConfig))))
}
