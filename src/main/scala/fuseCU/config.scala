package fuseCU

import agile.config._

case object WordWidth extends Field[Int](32)
case object SupportXS extends Field[Boolean](true)
case object ArraySize extends Field[Tuple2[Int,Int]]((64,64))

class BasePeConfig extends Config(
  (site, here, tail) => {
    case WordWidth => 16
    case ArraySize => (64,64)
  }
)

class XsConfig extends Config(
  (site, here, tail) => {
    case SupportXS => true
  }
)

class IsConfig extends Config(
  (site, here, tail) => {
    case SupportXS => false
  }
)

class XsPeConfig extends Config(
  new BasePeConfig ++
    new XsConfig
)

class IsPeConfig extends Config(
  new BasePeConfig ++
    new IsConfig
)
