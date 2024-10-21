package fuseCU

import chisel3._
import chisel3.util.Decoupled
import agile.config._

import scala.collection.mutable.ArrayBuffer

class BasePeArray (implicit val p : Parameters) extends Module {
  val dataWidth = p(WordWidth)
  val supportXS = p(SupportXS)
  val arrayDepth = p(ArraySize)._1
  val arrayWidth = p(ArraySize)._2

  val io = IO(new Bundle() {
    val config = if (supportXS) Some(Input(Bool())) else None // true is OS, false is IS
    val actIn =  if (supportXS) Some(Input(Vec(arrayDepth,UInt(dataWidth.W)))) else None
    val actOut = if (supportXS) Some(Output(Vec(arrayDepth, UInt(dataWidth.W)))) else None
    val wightIn = Input(Vec(arrayWidth, UInt(dataWidth.W)))
    val psumIn = Input(Vec(arrayDepth, UInt(dataWidth.W)))
    val wightOut = Output(Vec(arrayWidth,UInt(dataWidth.W)))
    val psumOut = Output(Vec(arrayDepth, UInt(dataWidth.W)))
  })

  val peArray = Vector.fill(arrayDepth, arrayWidth)(Module(new Pe))
  val seq0toW = (0 until  arrayWidth).toVector
  val seq0toD = (0 until  arrayDepth).toVector
  if (p(SupportXS)) {
    seq0toD.foreach(i => peArray(i)(0).io.actIn.get := io.actIn.get(i))
    seq0toD.foreach(i => io.actOut.get(i) := peArray(i)(arrayDepth-1).io.actOut.get)
    seq0toD.foreach(i => (1 until arrayWidth).foreach(j => peArray(i)(j).io.actIn.get := peArray(i)(j-1).io.actOut.get))
    seq0toD.foreach(i => seq0toW.foreach(j => peArray(i)(j).io.config.get := io.config.get))
  }
  seq0toD.foreach(i => peArray(i)(0).io.psumIn := io.psumIn(i))
  seq0toD.foreach(i => io.psumOut(i) := peArray(i)(arrayDepth-1).io.psumOut)
  seq0toW.foreach(i => peArray(0)(i).io.weightIn := io.wightIn(i))
  seq0toW.foreach(i => io.wightOut(i) := peArray(arrayWidth-1)(i).io.psumOut)
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
