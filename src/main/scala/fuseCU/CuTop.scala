package fuseCU

import chisel3._
import chisel3.util.Decoupled
import agile.config._


class CuTop(implicit val p: Parameters) extends Module {
  val dataWidth = p(WordWidth)
  val peArrayDepth = p(PeArraySize)._1
  val peArrayWidth = p(PeArraySize)._2
  val cuArrayDepth = p(CuArraySize)._1
  val cuArrayWidth = p(CuArraySize)._2
  assert(cuArrayWidth == cuArrayDepth)
  assert(peArrayWidth == peArrayDepth)

  val io = IO(new Bundle() {
    val execMode = Input(UInt(2.W)) // 0: 2x2; 1: 1x4; 2: 4x1
    val xsMode = Input(UInt(2.W)) // 0: ISOS fusion; 1: OS; 2: IS
    val ramReadPorts = Input(Vec(peArrayWidth * cuArrayWidth + peArrayDepth * cuArrayDepth, UInt(dataWidth.W)))
    val ramWritePorts = Output(Vec(peArrayWidth * cuArrayWidth + peArrayDepth * cuArrayDepth, UInt(dataWidth.W)))
  })

  assert((io.xsMode =/= 0.U & io.execMode === 0.U) | io.xsMode === 0.U)

  val fuseCuArray = Vector.fill(cuArrayDepth, cuArrayWidth)(Module(new FuseCU()))

  (0 until cuArrayDepth).foreach(i =>
    (0 until cuArrayWidth).foreach(j => {
      fuseCuArray(i)(j).io.weightFromRam := false.B
      fuseCuArray(i)(j).io.ioFromRam := false.B
    }))

  when(io.xsMode === 0.U) {
    when(io.execMode === 0.U) {
      fuseCuArray(0).foreach(cu => cu.io.weightFromRam := true.B)
      (0 until cuArrayDepth).foreach(i => {
        (0 until cuArrayWidth / 2).foreach(j => {
          fuseCuArray(i)(j).io.config.get := false.B
        })
        (cuArrayWidth / 2 until cuArrayWidth).foreach(j => {
          fuseCuArray(i)(j).io.config.get := true.B
        })
      })
    }.elsewhen(io.execMode === 1.U) {
      fuseCuArray(0).foreach(cu => cu.io.weightFromRam := true.B)
      fuseCuArray(cuArrayDepth / 2).foreach(cu => cu.io.weightFromRam := true.B)
      (0 until cuArrayDepth / 2).foreach(i =>
        (0 until cuArrayWidth).foreach(j => {
          fuseCuArray(i)(j).io.config.get := false.B
        }))
      (cuArrayDepth / 2 until cuArrayDepth).foreach(i =>
        (0 until (cuArrayWidth)).foreach(j => {
          fuseCuArray(i)(j).io.config.get := true.B
        }))
    }.otherwise {
      (cuArrayWidth / 2 until (cuArrayWidth)).foreach(j =>
        fuseCuArray(0)(j).io.weightFromRam := true.B)
      (0 until cuArrayDepth).foreach(i => {
        ((0 until cuArrayWidth / 4) ++ (cuArrayWidth / 2 until 3 * cuArrayWidth / 4)).foreach(j =>
          fuseCuArray(i)(j).io.config.get := false.B)
        ((cuArrayWidth / 4 until cuArrayWidth / 2) ++ (3 * cuArrayWidth / 4 until cuArrayWidth)).foreach(j =>
          fuseCuArray(i)(j).io.config.get := true.B)
      })
    }
  }.elsewhen(io.xsMode === 1.U) {
    fuseCuArray(0).foreach(cu => cu.io.weightFromRam := true.B)
    (0 until cuArrayDepth).foreach(i => fuseCuArray(i)(0).io.ioFromRam := true.B)
    fuseCuArray.foreach(_.foreach(_.io.config.get := true.B))
  }.otherwise {
    fuseCuArray(0).foreach(cu => cu.io.weightFromRam := true.B)
    fuseCuArray.foreach(_.foreach(_.io.config.get := false.B))
  }

  // PE port
  fuseCuArray.foreach(_.foreach(cu => {
    cu.io.fromPeIO := DontCare
    cu.io.fromPeWeight := DontCare
  }))
  (0 until cuArrayDepth).foreach(i => (1 until cuArrayWidth).foreach(j =>
    fuseCuArray(i)(j).io.fromPeIO := fuseCuArray(i)(j - 1).io.outIO))
  (0 until cuArrayWidth).foreach(i => (1 until cuArrayDepth).foreach(j =>
    fuseCuArray(j)(i).io.fromPeWeight := fuseCuArray(j - 1)(i).io.outWeight))
  (0 until cuArrayDepth / 2).foreach(i =>
    fuseCuArray(i + cuArrayDepth / 2)(0).io.fromPeIO := fuseCuArray(i)(cuArrayWidth - 1).io.outIO)
  (0 until cuArrayWidth / 2).foreach(i =>
    fuseCuArray(0)(i).io.fromPeWeight := fuseCuArray(cuArrayDepth - 1)(i + cuArrayWidth / 2).io.outWeight)

  // RAM port
  fuseCuArray.foreach(_.foreach(cu => {
    cu.io.fromRamIO := DontCare
    cu.io.fromRamWeight := DontCare
  }))
  (0 until cuArrayWidth).foreach(i => (0 until peArrayWidth).foreach(j =>
    fuseCuArray(0)(i).io.fromRamWeight(j) := io.ramReadPorts(i * peArrayWidth + j)))
  (0 until cuArrayWidth).foreach(i => (0 until peArrayWidth).foreach(j =>
    fuseCuArray(cuArrayDepth / 2)(i).io.fromRamWeight(j) :=
      io.ramReadPorts(peArrayWidth * cuArrayWidth + i * peArrayWidth + j)))
  (0 until cuArrayDepth).foreach(i => (0 until peArrayDepth).foreach(j =>
    fuseCuArray(i)(cuArrayWidth / 2).io.fromRamIO(j) := io.ramReadPorts(i * peArrayDepth + j)))
  (0 until cuArrayDepth).foreach(i => (0 until peArrayDepth).foreach(j =>
    fuseCuArray(i)(0).io.fromRamIO(j) :=
      io.ramReadPorts(peArrayDepth * cuArrayDepth + i * peArrayDepth + j)))

  (0 until cuArrayDepth).foreach(i => (0 until peArrayDepth).foreach(j =>
    io.ramWritePorts(i * peArrayDepth + j) := fuseCuArray(i)(cuArrayWidth / 2 - 1).io.outIO(j)))
  (0 until cuArrayDepth).foreach(i => (0 until peArrayDepth).foreach(j =>
    io.ramWritePorts(peArrayDepth * cuArrayDepth + i * peArrayDepth + j) :=
      fuseCuArray(i)(cuArrayWidth - 1).io.outIO(j)))


}

object CuTopGen extends App {

  import chisel3.stage.{ChiselStage, ChiselGeneratorAnnotation}

  // use "--help" to see more options
  val chiselArgs = Array("-X", "verilog", "-td", "verilog_gen_dir",
    "--emission-options", "disableMemRandomization,disableRegisterRandomization")
  (new chisel3.stage.ChiselStage).execute(
    chiselArgs, Seq(ChiselGeneratorAnnotation(() => new CuTop()(new TopConfig))))
}
