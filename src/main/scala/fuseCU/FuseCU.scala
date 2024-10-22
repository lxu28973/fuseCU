package fuseCU

import chisel3._
import chisel3.util.Decoupled
import agile.config._

class FuseCU(implicit val p: Parameters) extends Module {
  val dataWidth = p(WordWidth)
  val supportXS = p(SupportXS)
  val arrayDepth = p(PeArraySize)._1
  val arrayWidth = p(PeArraySize)._2

  val io = IO(new Bundle() {
    val config = if (supportXS) Some(Input(Bool())) else None // true is OS, false is IS
    val weightFromRam = Input(Bool())
    val ioFromRam = Input(Bool())
    val fromRamWeight = Input(Vec(arrayWidth, UInt(dataWidth.W)))
    val fromPeWeight = Input(Vec(arrayWidth, UInt(dataWidth.W)))
    val fromRamIO = Input(Vec(arrayDepth, UInt(dataWidth.W)))
    val fromPeIO = Input(Vec(arrayDepth, UInt(dataWidth.W)))
    val outWeight = Output(Vec(arrayWidth, UInt(dataWidth.W)))
    val outIO = Output(Vec(arrayDepth, UInt(dataWidth.W)))
  })

  val basePeArray = Module(new BasePeArray())
  val actOrPsum = Mux(io.ioFromRam, io.fromRamIO, io.fromPeIO)
  if (supportXS) basePeArray.io.actIn.get := actOrPsum
  if (supportXS) basePeArray.io.config.get := io.config.get
  basePeArray.io.psumIn := actOrPsum
  basePeArray.io.wightIn := Mux(io.weightFromRam, io.fromRamWeight, io.fromPeWeight)

  io.outWeight := basePeArray.io.wightOut
  if (supportXS) {
    io.outIO := Mux(io.config.get, basePeArray.io.actOut.get, basePeArray.io.psumOut)
  } else
    io.outIO := basePeArray.io.psumOut

}

object FuseCUGen extends App {
  import chisel3.stage.{ChiselStage, ChiselGeneratorAnnotation}

  // use "--help" to see more options
  val chiselArgs = Array("-X", "verilog", "-td", "verilog_gen_dir",
    "--emission-options", "disableMemRandomization,disableRegisterRandomization")
  (new chisel3.stage.ChiselStage).execute(
    chiselArgs, Seq(ChiselGeneratorAnnotation(() => new FuseCU()(new XsPeConfig))))
}
