module Pe(
  input         clock,
  input         reset,
  input         io_xsConfig,
  input         io_initIn,
  output        io_initOut,
  input  [7:0]  io_actIn,
  output [7:0]  io_actOut,
  input  [7:0]  io_weightIn,
  output [7:0]  io_weightOut,
  input  [31:0] io_psumIn,
  output [31:0] io_psumOut
);
  reg [7:0] actReg; // @[Pe.scala 27:23]
  reg [7:0] weightReg; // @[Pe.scala 28:26]
  reg [31:0] psumReg; // @[Pe.scala 29:24]
  wire [31:0] psumFrom = io_xsConfig ? $signed(psumReg) : $signed(io_psumIn); // @[Pe.scala 32:36]
  reg  initReg; // @[Pe.scala 34:24]
  wire [15:0] mulOut = $signed(actReg) * $signed(weightReg); // @[Pe.scala 40:23]
  wire [31:0] _GEN_6 = {{16{mulOut[15]}},mulOut}; // @[Pe.scala 41:25]
  wire [31:0] addOut = $signed(psumFrom) + $signed(_GEN_6); // @[Pe.scala 41:25]
  assign io_initOut = initReg; // @[Pe.scala 35:14]
  assign io_actOut = actReg; // @[Pe.scala 44:13]
  assign io_weightOut = weightReg; // @[Pe.scala 45:16]
  assign io_psumOut = psumReg; // @[Pe.scala 46:14]
  always @(posedge clock) begin
    if (reset) begin // @[Pe.scala 27:23]
      actReg <= 8'sh0; // @[Pe.scala 27:23]
    end else if (initReg) begin // @[Pe.scala 48:17]
      if (io_xsConfig) begin // @[Pe.scala 50:29]
        actReg <= 8'sh0; // @[Pe.scala 52:16]
      end else begin
        actReg <= io_actIn; // @[Pe.scala 56:16]
      end
    end else if (io_xsConfig) begin // @[Pe.scala 31:35]
      actReg <= io_actIn;
    end
    if (reset) begin // @[Pe.scala 28:26]
      weightReg <= 8'sh0; // @[Pe.scala 28:26]
    end else if (initReg) begin // @[Pe.scala 48:17]
      weightReg <= 8'sh0;
    end else begin
      weightReg <= io_weightIn; // @[Pe.scala 38:13]
    end
    if (reset) begin // @[Pe.scala 29:24]
      psumReg <= 32'sh0; // @[Pe.scala 29:24]
    end else if (initReg) begin // @[Pe.scala 48:17]
      if (io_xsConfig) begin // @[Pe.scala 50:29]
        psumReg <= io_psumIn; // @[Pe.scala 51:17]
      end else begin
        psumReg <= 32'sh0; // @[Pe.scala 55:17]
      end
    end else begin
      psumReg <= addOut; // @[Pe.scala 42:11]
    end
    initReg <= io_initIn; // @[Pe.scala 34:24]
  end
endmodule
