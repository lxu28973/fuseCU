package fuseCU

import chisel3._
import chisel3.util._
import agile.config._

class FuseCU(implicit val p: Parameters) extends Module {
  val dataWidth = p(WordWidth)
  val supportXS = p(SupportXS)
  val arrayDepth = p(PeArraySize)._1
  val arrayWidth = p(PeArraySize)._2

  val io = IO(new Bundle() {
    val xsConfig = if (supportXS) Some(Input(Bool())) else None // true is OS, false is IS
    val quant = Input(UInt((log2Ceil(4 * dataWidth)).W))

    val initIn = Input(Vec(arrayDepth, Bool()))
    val initOut = Output(Vec(arrayDepth, Bool()))
    val weightFromRam = Input(Bool())
    val actFromRam = Input(Bool())
    val psumFromRam = Input(Bool())
    val fromRamWeight = Input(Vec(arrayWidth, SInt(dataWidth.W)))
    val fromPeWeight = Input(Vec(arrayWidth, SInt(dataWidth.W)))
    val fromRamAct = Input(Vec(arrayDepth, SInt(dataWidth.W)))
    val fromPeAct = Input(Vec(arrayDepth, SInt(dataWidth.W)))
    val fromRamPsum = Input(Vec(arrayDepth, SInt((4 * dataWidth).W)))
    val fromPePsum = Input(Vec(arrayDepth, SInt((4 * dataWidth).W)))
    val outWeight = Output(Vec(arrayWidth, SInt(dataWidth.W)))
    val outAct = Output(Vec(arrayDepth, SInt(dataWidth.W)))
    val outPsum = Output(Vec(arrayDepth, SInt((4 * dataWidth).W)))
  })

  val xsConfig = if (supportXS) Some(RegNext(io.xsConfig.get)) else None // true is OS, false is IS
  val quant = RegNext(io.quant)

  val basePeArray = Module(new BasePeArray())
  val act = Mux(io.actFromRam, io.fromRamAct, io.fromPeAct)
  if (supportXS) basePeArray.io.actIn := act
  val psum = Mux(io.psumFromRam, io.fromRamPsum, io.fromPePsum)
  if (supportXS) basePeArray.io.xsConfig.get := xsConfig.get
  basePeArray.io.initIn := io.initIn
  basePeArray.io.psumIn := psum
  basePeArray.io.wightIn := Mux(io.weightFromRam, io.fromRamWeight, io.fromPeWeight)

  io.outWeight := basePeArray.io.wightOut

  val quantPsum = Wire(Vec(arrayDepth, SInt(dataWidth.W)))
  (0 until arrayDepth).foreach(i => quantPsum(i) := basePeArray.io.psumOut(0) >> quant)
  if (supportXS) {
    io.outAct := Mux(xsConfig.get, basePeArray.io.actOut, quantPsum)
  }
  io.outPsum := basePeArray.io.psumOut
  io.initOut := basePeArray.io.initOut

}

object FuseCUGen extends App {

  import chisel3.stage.{ChiselStage, ChiselGeneratorAnnotation}

  // use "--help" to see more options
  val chiselArgs = Array("-X", "verilog", "-td", "verilog_gen_dir",
    "--emission-options", "disableMemRandomization,disableRegisterRandomization")
  (new chisel3.stage.ChiselStage).execute(
    chiselArgs, Seq(ChiselGeneratorAnnotation(() => new FuseCU()(new XsPeConfig))))
}
