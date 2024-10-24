package fuseCU

import chisel3._
import chisel3.util._
import agile.config._

class SoftMax(implicit val p: Parameters) extends Module {
  val dataWidth = 4 * p(WordWidth)
  val peArrayWidth = p(PeArraySize)._2

  val io = IO(new Bundle() {
    val start = Input(Bool())
    val dataIn = Input(SInt((dataWidth).W))
    val dataOut = Output(SInt((dataWidth).W))
  })

  val max = Wire(SInt(dataWidth.W))
  val maxReg = RegInit(0.asSInt(dataWidth.W))
  val subInReg = RegInit(0.asSInt(dataWidth.W))
  val subOutReg = RegInit(0.asSInt(dataWidth.W))
  val expReg = RegInit(0.asSInt(dataWidth.W))

  val dataQueue = Reg(Vec(peArrayWidth, SInt(dataWidth.W)))

  max := Mux(maxReg < io.dataIn, io.dataIn, maxReg)

  when(io.start) {
    maxReg := io.dataIn
  }.otherwise {
    maxReg := max
  }
  dataQueue(0) := io.dataIn
  (0 until peArrayWidth - 1).foreach(i => dataQueue(i + 1) := dataQueue(i))

  subInReg := Mux(io.start, max, subInReg)
  subOutReg := dataQueue(peArrayWidth - 1) - subInReg

  val i2fltUnit = Module(new DW_fp_i2flt)
  val expUnit = Module(new DW_fp_exp)
  val flt2iUnit = Module(new DW_fp_flt2i)
  i2fltUnit.io.rnd := 0.U
  flt2iUnit.io.rnd := 0.U
  i2fltUnit.io.a := subOutReg
  expUnit.io.a := i2fltUnit.io.z
  flt2iUnit.io.a := expUnit.io.z
  expReg := flt2iUnit.io.z

  io.dataOut := expReg

}

class DW_fp_flt2i(implicit val p: Parameters) extends BlackBox(Map("sig_width" -> p(SigWidth), "exp_width" -> p(ExpWidth), "isize" -> 4 * p(WordWidth), "ieee_compliance" -> 0)) {
  val io = IO(new Bundle() {
    val a = Input(SInt((p(SigWidth) + p(ExpWidth) + 1).W))
    val rnd = Input(UInt(3.W))
    val z = Output(SInt((4*p(WordWidth)).W))
    val status = Output(UInt(8.W))
  })
}

class DW_fp_i2flt(implicit val p: Parameters) extends BlackBox(Map("sig_width" -> p(SigWidth), "exp_width" -> p(ExpWidth), "isize" -> 4 * p(WordWidth), "isign" -> 1)) {
  val io = IO(new Bundle() {
    val a = Input(SInt((4 * p(WordWidth)).W))
    val rnd = Input(UInt(3.W))
    val z = Output(SInt((p(SigWidth) + p(ExpWidth) + 1).W))
    val status = Output(UInt(8.W))
  })
}

class DW_fp_exp(implicit val p: Parameters) extends BlackBox(Map("sig_width" -> p(SigWidth), "exp_width" -> p(ExpWidth), "ieee_compliance" -> 0, "arch" -> 0)) {
  val io = IO(new Bundle() {
    val a = Input(SInt((p(SigWidth) + p(ExpWidth) + 1).W))
    val z = Output(SInt((p(SigWidth) + p(ExpWidth) + 1).W))
    val status = Output(UInt(8.W))
  })
}

object SoftMaxGen extends App {

  import chisel3.stage.{ChiselStage, ChiselGeneratorAnnotation}

  // use "--help" to see more options
  val chiselArgs = Array("-X", "verilog", "-td", "verilog_gen_dir", "-e", "verilog",
    "--emission-options", "disableMemRandomization,disableRegisterRandomization")
  (new chisel3.stage.ChiselStage).execute(
    chiselArgs, Seq(ChiselGeneratorAnnotation(() => new SoftMax()(new TopConfig))))
}
