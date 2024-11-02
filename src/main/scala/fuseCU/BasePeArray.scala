package fuseCU

import chisel3._
import chisel3.util.Decoupled
import agile.config._

class BasePeArray (implicit val p : Parameters) extends Module {
  val dataWidth = p(WordWidth)
  val supportXS = p(SupportXS)
  val arrayDepth = p(PeArraySize)._1
  val arrayWidth = p(PeArraySize)._2

  val io = IO(new Bundle() {
    val xsConfig = if (supportXS) Some(Input(Bool())) else None // true is OS, false is IS
    val initIn = Input(Vec(arrayDepth, Bool()))
    val initOut = Output(Vec(arrayDepth, Bool()))
    val actIn = Input(Vec(arrayDepth,SInt(dataWidth.W)))
    val actOut = Output(Vec(arrayDepth, SInt(dataWidth.W)))
    val wightIn = Input(Vec(arrayWidth, SInt(dataWidth.W)))
    val psumIn = Input(Vec(arrayDepth, SInt((4*dataWidth).W)))
    val wightOut = Output(Vec(arrayWidth,SInt(dataWidth.W)))
    val psumOut = Output(Vec(arrayDepth, SInt((4*dataWidth).W)))
  })

  val xsConfig = if (supportXS) Some(RegNext(io.xsConfig.get)) else None // true is OS, false is IS

  val pes = Vector.fill(arrayDepth, arrayWidth)(Module(new Pe))
  val seq0toW = (0 until  arrayWidth).toVector
  val seq0toD = (0 until  arrayDepth).toVector
  seq0toD.foreach(i => pes(i)(0).io.actIn := io.actIn(i))
  seq0toD.foreach(i => io.actOut(i) := pes(i)(arrayDepth-1).io.actOut)
  seq0toD.foreach(i => (1 until arrayWidth).foreach(j => pes(i)(j).io.actIn:= pes(i)(j-1).io.actOut))
  seq0toD.foreach(i => seq0toW.foreach(j => pes(i)(j).io.xsConfig.get := xsConfig.get))
  seq0toD.foreach(i => pes(i)(0).io.psumIn := io.psumIn(i))
  seq0toD.foreach(i => pes(i)(0).io.initIn := io.initIn(i))
  seq0toD.foreach(i => io.psumOut(i) := pes(i)(arrayDepth-1).io.psumOut)
  seq0toD.foreach(i => io.initOut(i) := pes(i)(arrayDepth-1).io.initOut)
  seq0toW.foreach(i => pes(0)(i).io.weightIn := io.wightIn(i))
  seq0toW.foreach(i => io.wightOut(i) := pes(arrayWidth-1)(i).io.weightOut)
  seq0toD.foreach(i => (1 until arrayWidth).foreach(j => pes(i)(j).io.psumIn := pes(i)(j-1).io.psumOut))
  seq0toD.foreach(i => (1 until arrayWidth).foreach(j => pes(i)(j).io.initIn := pes(i)(j-1).io.initOut))
  seq0toW.foreach(i => (1 until arrayDepth).foreach(j => pes(j)(i).io.weightIn := pes(j-1)(i).io.weightOut))

}

object BasePeArrayGen extends App {
  import chisel3.stage.{ChiselStage, ChiselGeneratorAnnotation}

  // use "--help" to see more options
  val chiselArgs = Array("-X", "verilog", "-td", "verilog_gen_dir", "-e", "verilog",
    "--emission-options", "disableMemRandomization,disableRegisterRandomization")
  (new chisel3.stage.ChiselStage).execute(
    chiselArgs, Seq(ChiselGeneratorAnnotation(() => new BasePeArray()(new IsPeConfig))))
}
