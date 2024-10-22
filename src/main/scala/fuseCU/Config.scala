package fuseCU

import agile.config._

case object WordWidth extends Field[Int](32)

case object SupportXS extends Field[Boolean](true)

case object PeArraySize extends Field[Tuple2[Int, Int]]((64, 64))

case object CuArraySize extends Field[Tuple2[Int, Int]]((4, 4))

class WithBasePeConfig extends Config(
  (site, here, tail) => {
    case WordWidth => 8
    case PeArraySize => (64, 64)
  }
)

class WithXsConfig extends Config(
  (site, here, tail) => {
    case SupportXS => true
  }
)

class WithIsConfig extends Config(
  (site, here, tail) => {
    case SupportXS => false
  }
)

class XsPeConfig extends Config(
  new WithBasePeConfig ++
    new WithXsConfig
)

class IsPeConfig extends Config(
  new WithBasePeConfig ++
    new WithIsConfig
)

class WithTopConfig extends Config(
  (site, here, tail) => {
    case CuArraySize => (4, 4)
  }
)

class TopConfig extends Config(
  new WithBasePeConfig ++
    new WithXsConfig ++
    new WithTopConfig
)