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
    val weightFromRam = Input(Bool())
    val actFromRam = Input(Bool())
    val psumFromRam = Input(Bool())
    val fromRamWeight = Input(Vec(arrayWidth, UInt(dataWidth.W)))
    val fromPeWeight = Input(Vec(arrayWidth, UInt(dataWidth.W)))
    val fromRamAct = Input(Vec(arrayDepth, UInt(dataWidth.W)))
    val fromPeAct = Input(Vec(arrayDepth, UInt(dataWidth.W)))
    val fromRamPsum = Input(Vec(arrayDepth, UInt((4 * dataWidth).W)))
    val fromPePsum = Input(Vec(arrayDepth, UInt((4 * dataWidth).W)))
    val outWeight = Output(Vec(arrayWidth, UInt(dataWidth.W)))
    val outAct = Output(Vec(arrayDepth, UInt(dataWidth.W)))
    val outPsum = Output(Vec(arrayDepth, UInt((4 * dataWidth).W)))
  })

  val xsConfig = if (supportXS) Some(RegNext(io.xsConfig.get)) else None // true is OS, false is IS
  val quant = RegNext(io.quant)
  val weightFromRam = RegNext(io.weightFromRam)
  val actFromRam = RegNext(io.actFromRam)
  val psumFromRam = RegNext(io.psumFromRam)
  val fromRamWeight = RegNext(io.fromRamWeight)
  val fromPeWeight = RegNext(io.fromPeWeight)
  val fromRamAct = RegNext(io.fromRamAct)
  val fromPeAct = RegNext(io.fromPeAct)
  val fromRamPsum = RegNext(io.fromRamPsum)
  val fromPePsum = RegNext(io.fromPePsum)

  val basePeArray = Module(new BasePeArray())
  val act = Mux(actFromRam, fromRamAct, fromPeAct)
  if (supportXS) basePeArray.io.actIn.get := act
  val psum = Mux(psumFromRam, fromRamPsum, fromPePsum)
  if (supportXS) basePeArray.io.xsConfig.get := xsConfig.get
  basePeArray.io.psumIn := psum
  basePeArray.io.wightIn := Mux(weightFromRam, fromRamWeight, fromPeWeight)

  io.outWeight := basePeArray.io.wightOut

  val quantPsum = Wire(Vec(arrayDepth,UInt(dataWidth.W)))
  (0 until arrayDepth).foreach(i => quantPsum(i) := basePeArray.io.psumOut(0) >> quant)
  if (supportXS) {
    io.outAct := Mux(xsConfig.get, basePeArray.io.actOut.get, quantPsum)
  }
  io.outPsum := basePeArray.io.psumOut

}

object FuseCUGen extends App {

  import chisel3.stage.{ChiselStage, ChiselGeneratorAnnotation}

  // use "--help" to see more options
  val chiselArgs = Array("-X", "verilog", "-td", "verilog_gen_dir",
    "--emission-options", "disableMemRandomization,disableRegisterRandomization")
  (new chisel3.stage.ChiselStage).execute(
    chiselArgs, Seq(ChiselGeneratorAnnotation(() => new FuseCU()(new XsPeConfig))))
}
