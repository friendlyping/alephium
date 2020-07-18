package org.alephium.protocol.model

import org.alephium.protocol.config.ConsensusConfigFixture
import org.alephium.serde._
import org.alephium.util.AlephiumSpec

class BlockSpec extends AlephiumSpec with ConsensusConfigFixture with NoIndexModelGeneratorsLike {
  it should "serde" in {
    forAll(blockGen) { block =>
      val bytes  = serialize[Block](block)
      val output = deserialize[Block](bytes).toOption.get
      output is block
    }
  }

  it should "hash" in {
    forAll(blockGen) { block =>
      block.hash is block.header.hash
    }
  }

  it should "calculate chain index" in {
    forAll(chainIndexGen) { chainIndex =>
      forAll(blockGen(chainIndex)) { block =>
        block.chainIndex is chainIndex
      }
    }
  }
}
