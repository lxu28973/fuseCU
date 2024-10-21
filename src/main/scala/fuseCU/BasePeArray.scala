package fuseCU

import chisel3._
import chisel3.util.Decoupled
import agile.config._

import scala.collection.mutable.ArrayBuffer

class PeArrayWeightBundle (implicit p: Parameters) extends Bundle {
  val dataWidth = p(WordWidth)
  val supportXS = p(SupportXS)

  val weight = (UInt(dataWidth.W))
}

class PeArrayActBundle (implicit p: Parameters) extends Bundle {
  val dataWidth = p(WordWidth)
  val supportXS = p(SupportXS)

  val act = if (supportXS) Some((UInt(dataWidth.W))) else None
  val psum = (UInt(dataWidth.W))
}

class BasePeArray (implicit val p : Parameters) extends Module {
  val arrayDepth = p(ArraySize)._1
  val arrayWidth = p(ArraySize)._2

  val io = IO(new Bundle() {
    val config = if (p(SupportXS)) Some(Input(Bool())) else None // true is OS, false is IS
    val wightIn = Input(Vec(arrayWidth, new PeArrayWeightBundle()))
    val actIn = Input(Vec(arrayDepth, new PeArrayActBundle()))
    val wightOut = Output(Vec(arrayWidth, new PeArrayWeightBundle()))
    val actOut = Output(Vec(arrayDepth, new PeArrayActBundle()))
  })

  val peArray = Vector.fill(arrayDepth, arrayWidth)(Module(new Pe))
  val seq0toW = (0 until  arrayWidth).toVector
  val seq0toD = (0 until  arrayDepth).toVector
  if (p(SupportXS)) {
    seq0toD.foreach(i => peArray(i)(0).io.actIn.get := io.actIn(i).act.get)
    seq0toD.foreach(i => io.actOut(i).act.get := peArray(i)(arrayDepth-1).io.actOut.get)
    seq0toD.foreach(i => (1 until arrayWidth).foreach(j => peArray(i)(j).io.actIn.get := peArray(i)(j-1).io.actOut.get))
    seq0toD.foreach(i => seq0toW.foreach(j => peArray(i)(j).io.config.get := io.config.get))
  }
  seq0toD.foreach(i => peArray(i)(0).io.psumIn := io.actIn(i).psum)
  seq0toD.foreach(i => io.actOut(i).psum := peArray(i)(arrayDepth-1).io.psumOut)
  seq0toW.foreach(i => peArray(0)(i).io.weightIn := io.wightIn(i).weight)
  seq0toW.foreach(i => io.wightOut(i).weight := peArray(arrayWidth-1)(i).io.psumOut)
  seq0toD.foreach(i => (1 until arrayWidth).foreach(j => peArray(i)(j).io.psumIn := peArray(i)(j-1).io.psumOut))
  seq0toW.foreach(i => (1 until arrayDepth).foreach(j => peArray(j)(i).io.weightIn := peArray(j-1)(i).io.weightOut))

}

object BasePeArrayGen extends App {
  import chisel3.stage.{ChiselStage, ChiselGeneratorAnnotation}

  // use "--help" to see more options
  val chiselArgs = Array("-X", "verilog", "-td", "verilog_gen_dir",
    "--emission-options", "disableMemRandomization,disableRegisterRandomization")
  (new chisel3.stage.ChiselStage).execute(
    chiselArgs, Seq(ChiselGeneratorAnnotation(() => new BasePeArray()(new XsPeConfig))))
}
