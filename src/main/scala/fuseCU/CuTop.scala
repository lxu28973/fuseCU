package fuseCU

import chisel3._
import chisel3.util._
import agile.config._

class CuTopIO (implicit val p:Parameters) extends Bundle {
  val dataWidth = p(WordWidth)
  val peArrayDepth = p(PeArraySize)._1
  val peArrayWidth = p(PeArraySize)._2
  val cuArrayDepth = p(CuArraySize)._1
  val cuArrayWidth = p(CuArraySize)._2

  val execMode = Input(UInt(2.W)) // 0: 2x2; 1: 1x4; 2: 4x1
  val xsMode = Input(UInt(2.W)) // 0: ISOS fusion; 1: OS; 2: IS
  val quant = Input(UInt((log2Ceil(4 * dataWidth)).W))

  val ramReadPorts = Input(Vec(peArrayWidth * cuArrayWidth + peArrayDepth * cuArrayDepth, SInt(dataWidth.W)))
  val ramWritePorts = Output(Vec(peArrayWidth * cuArrayWidth + peArrayDepth * cuArrayDepth, SInt(dataWidth.W)))
}

class CuTop(implicit val p: Parameters) extends Module {
  val dataWidth = p(WordWidth)
  val peArrayDepth = p(PeArraySize)._1
  val peArrayWidth = p(PeArraySize)._2
  val cuArrayDepth = p(CuArraySize)._1
  val cuArrayWidth = p(CuArraySize)._2
  assert(cuArrayWidth == cuArrayDepth)
  assert(peArrayWidth == peArrayDepth)

  val io = IO(new CuTopIO)

  assert((io.xsMode =/= 0.U & io.execMode === 0.U) | io.xsMode === 0.U)

  val execMode = RegNext(io.execMode) // 0: 2x2; 1: 1x4; 2: 4x1
  val xsMode = RegNext(io.xsMode) // 0: ISOS fusion; 1: OS; 2: IS
  val quant = RegNext(io.quant)

  val fuseCuArray = Vector.fill(cuArrayDepth, cuArrayWidth)(Module(new FuseCU()))

  fuseCuArray.foreach(_.foreach(_.io.quant := quant))

  (0 until cuArrayDepth).foreach(i =>
    (0 until cuArrayWidth).foreach(j => {
      fuseCuArray(i)(j).io.weightFromRam := false.B
      fuseCuArray(i)(j).io.actFromRam := false.B
      fuseCuArray(i)(j).io.psumFromRam := false.B
    }))

  when(xsMode === 0.U) {
    when(execMode === 0.U) {
      fuseCuArray(0).foreach(cu => cu.io.weightFromRam := true.B)
      (0 until cuArrayDepth).foreach(i => {
        (0 until cuArrayWidth / 2).foreach(j => {
          fuseCuArray(i)(j).io.xsConfig.get := false.B
        })
        (cuArrayWidth / 2 until cuArrayWidth).foreach(j => {
          fuseCuArray(i)(j).io.xsConfig.get := true.B
        })
      })
    }.elsewhen(execMode === 1.U) {
      fuseCuArray(0).foreach(cu => cu.io.weightFromRam := true.B)
      fuseCuArray(cuArrayDepth / 2).foreach(cu => cu.io.weightFromRam := true.B)
      (0 until cuArrayDepth / 2).foreach(i =>
        (0 until cuArrayWidth).foreach(j => {
          fuseCuArray(i)(j).io.xsConfig.get := false.B
        }))
      (cuArrayDepth / 2 until cuArrayDepth).foreach(i =>
        (0 until (cuArrayWidth)).foreach(j => {
          fuseCuArray(i)(j).io.xsConfig.get := true.B
        }))
    }.otherwise {
      (cuArrayWidth / 2 until (cuArrayWidth)).foreach(j =>
        fuseCuArray(0)(j).io.weightFromRam := true.B)
      (0 until cuArrayDepth).foreach(i => {
        ((0 until cuArrayWidth / 4) ++ (cuArrayWidth / 2 until 3 * cuArrayWidth / 4)).foreach(j =>
          fuseCuArray(i)(j).io.xsConfig.get := false.B)
        ((cuArrayWidth / 4 until cuArrayWidth / 2) ++ (3 * cuArrayWidth / 4 until cuArrayWidth)).foreach(j =>
          fuseCuArray(i)(j).io.xsConfig.get := true.B)
      })
    }
  }.elsewhen(xsMode === 1.U) {
    fuseCuArray(0).foreach(cu => cu.io.weightFromRam := true.B)
    (0 until cuArrayDepth).foreach(i => fuseCuArray(i)(0).io.actFromRam := true.B)
    fuseCuArray.foreach(_.foreach(_.io.xsConfig.get := true.B))
  }.otherwise {
    fuseCuArray(0).foreach(cu => cu.io.weightFromRam := true.B)
    (0 until cuArrayDepth).foreach(i => fuseCuArray(i)(0).io.psumFromRam := true.B)
    fuseCuArray.foreach(_.foreach(_.io.xsConfig.get := false.B))
  }

  // PE port
  fuseCuArray.foreach(_.foreach(cu => {
    cu.io.fromPeAct := DontCare
    cu.io.fromPePsum := DontCare
    cu.io.fromPeWeight := DontCare
  }))
  (0 until cuArrayDepth).foreach(i => (1 until cuArrayWidth).foreach(j =>
    fuseCuArray(i)(j).io.fromPeAct := fuseCuArray(i)(j - 1).io.outAct))
  (0 until cuArrayDepth).foreach(i => (1 until cuArrayWidth).foreach(j =>
    fuseCuArray(i)(j).io.fromPePsum := fuseCuArray(i)(j - 1).io.outPsum))
  (0 until cuArrayWidth).foreach(i => (1 until cuArrayDepth).foreach(j =>
    fuseCuArray(j)(i).io.fromPeWeight := fuseCuArray(j - 1)(i).io.outWeight))
  (0 until cuArrayDepth / 2).foreach(i =>
    fuseCuArray(i + cuArrayDepth / 2)(0).io.fromPeAct := fuseCuArray(i)(cuArrayWidth - 1).io.outAct)
  (0 until cuArrayDepth / 2).foreach(i =>
    fuseCuArray(i + cuArrayDepth / 2)(0).io.fromPePsum := fuseCuArray(i)(cuArrayWidth - 1).io.outPsum)
  (0 until cuArrayWidth / 2).foreach(i =>
    fuseCuArray(0)(i).io.fromPeWeight := fuseCuArray(cuArrayDepth - 1)(i + cuArrayWidth / 2).io.outWeight)

  // RAM port
  fuseCuArray.foreach(_.foreach(cu => {
    cu.io.fromRamAct := DontCare
    cu.io.fromRamPsum := DontCare
    cu.io.fromRamWeight := DontCare
  }))
  (0 until cuArrayWidth).foreach(i => (0 until peArrayWidth).foreach(j =>
    fuseCuArray(0)(i).io.fromRamWeight(j) := io.ramReadPorts(i * peArrayWidth + j)))
  (0 until cuArrayWidth).foreach(i => (0 until peArrayWidth).foreach(j =>
    fuseCuArray(cuArrayDepth / 2)(i).io.fromRamWeight(j) :=
      io.ramReadPorts(peArrayWidth * cuArrayWidth + i * peArrayWidth + j)))
  (0 until cuArrayDepth).foreach(i => (0 until peArrayDepth).foreach(j =>
    fuseCuArray(i)(cuArrayWidth / 2).io.fromRamAct(j) := io.ramReadPorts(i * peArrayDepth + j)))
  (0 until cuArrayDepth).foreach(i => (0 until peArrayDepth).foreach(j =>
    fuseCuArray(i)(0).io.fromRamAct(j) :=
      io.ramReadPorts(peArrayDepth * cuArrayDepth + i * peArrayDepth + j)))
  (0 until cuArrayDepth).foreach(i => (0 until peArrayDepth).foreach(j =>
    fuseCuArray(i)(cuArrayWidth / 2).io.fromRamPsum(j) := io.ramReadPorts(i * peArrayDepth + j)))
  (0 until cuArrayDepth).foreach(i => (0 until peArrayDepth).foreach(j =>
    fuseCuArray(i)(0).io.fromRamPsum(j) :=
      io.ramReadPorts(peArrayDepth * cuArrayDepth + i * peArrayDepth + j)))

  (0 until cuArrayDepth).foreach(i => (0 until peArrayDepth).foreach(j =>
    io.ramWritePorts(i * peArrayDepth + j) := fuseCuArray(i)(cuArrayWidth / 2 - 1).io.outAct(j)))
  (0 until cuArrayDepth).foreach(i => (0 until peArrayDepth).foreach(j =>
    io.ramWritePorts(peArrayDepth * cuArrayDepth + i * peArrayDepth + j) :=
      fuseCuArray(i)(cuArrayWidth - 1).io.outAct(j)))
  (0 until cuArrayDepth).foreach(i => (0 until peArrayDepth).foreach(j =>
    io.ramWritePorts(i * peArrayDepth + j) := fuseCuArray(i)(cuArrayWidth / 2 - 1).io.outPsum(j)))
  (0 until cuArrayDepth).foreach(i => (0 until peArrayDepth).foreach(j =>
    io.ramWritePorts(peArrayDepth * cuArrayDepth + i * peArrayDepth + j) :=
      fuseCuArray(i)(cuArrayWidth - 1).io.outPsum(j)))

}

object CuTopGen extends App {

  import chisel3.stage.{ChiselStage, ChiselGeneratorAnnotation}

  // use "--help" to see more options
  val chiselArgs = Array("-X", "verilog", "-td", "verilog_gen_dir", "-e", "verilog",
    "--emission-options", "disableMemRandomization,disableRegisterRandomization")
  (new chisel3.stage.ChiselStage).execute(
    chiselArgs, Seq(ChiselGeneratorAnnotation(() => new CuTop()(new TopConfig))))
}
