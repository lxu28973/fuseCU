RTL source code for our DAC 2025 paper, "Principle-based Dataflow Optimization for Communication Lower Bound in Operator-Fused Tensor Accelerator"


## Setup

### Dependencies

#### JDK 8 or newer

We recommend LTS releases Java 8 and Java 11. You can install the JDK as recommended by your operating system, or use the prebuilt binaries from [AdoptOpenJDK](https://adoptopenjdk.net/).

#### SBT

SBT is the most common built tool in the Scala community. You can download it [here](https://www.scala-sbt.org/download.html).  

### How to get started

#### Clone repository

```sh
git clone git@github.com:lxu28973/fuseCU.git
cd fuseCU
```

### Did it work?

You should now have a working Chisel3 project.

You can run the included test with:
```sh
sbt test
```

You should see a whole bunch of output that ends with something like the following lines
```
[info] Tests: succeeded 1, failed 0, canceled 0, ignored 0, pending 0
[info] All tests passed.
[success] Total time: 5 s, completed Dec 16, 2020 12:18:44 PM
```
If you see the above then...

### It worked!

You are ready to generate fuseCU rtl code. 

## Generate fuseCU rtl code

You only need to run: 
```sh
sbt "runMain fuseCU.TopWrapGen" -mem 5000
```

The generated Verilog code will be in the `verilog_gen_dir`.

For those who do not want to use Chisel, we also provide the Verilog version in the `pre-gen` directory.
However, we recommend the Chisel version, as it allows you to modify parameters and generate different hardware.

## How to cite?
Lei Xu, Chen Yin, Zelong Yuan, Weiguang Sheng, Jianfei Jiang, Qin Wang, Naifeng Jing. Principle-based Dataflow Optimization for Communication Lower Bound in Operator-Fused Tensor Accelerator. In 62nd ACM/IEEE Design Automation Conference (DAC ’25).
