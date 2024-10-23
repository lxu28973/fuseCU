package fuseCU

import chisel3._
import chisel3.util._
import agile.config._

class TopWrap (implicit val p : Parameters )  extends RawModule {
  val clk  = IO(Input(Clock()))
  val rstn = IO(Input(Bool()))
  val io = IO(new CuTopIO)

  val cuTop = withClockAndReset(clk, !rstn){ Module(new CuTop()) }
  cuTop.io <> io
}

object TopWrapGen extends App {

  import chisel3.stage.{ChiselStage, ChiselGeneratorAnnotation}

  // use "--help" to see more options
  val chiselArgs = Array("-X", "verilog", "-td", "verilog_gen_dir", "-e", "verilog",
    "--emission-options", "disableMemRandomization,disableRegisterRandomization")
  (new chisel3.stage.ChiselStage).execute(
    chiselArgs, Seq(ChiselGeneratorAnnotation(() => new TopWrap()(new TopConfig))))
}
