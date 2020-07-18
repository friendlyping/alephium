package org.alephium.appserver

import akka.http.scaladsl.model._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.testkit.TestProbe
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import org.scalatest.EitherValues
import org.scalatest.concurrent.ScalaFutures

import org.alephium.appserver.ApiModel._
import org.alephium.flow.client.Miner
import org.alephium.flow.platform.Mode
import org.alephium.protocol.model.ChainIndex
import org.alephium.serde.serialize
import org.alephium.util._

class RestServerSpec
    extends AlephiumSpec
    with ScalatestRouteTest
    with EitherValues
    with ScalaFutures
    with NumericHelpers {
  import ServerFixture._

  it should "call GET /blockflow" in new RestServerFixture {
    Get(s"/blockflow?fromTs=0&toTs=0") ~> server.route ~> check {
      status is StatusCodes.OK
      responseAs[FetchResponse] is dummyFetchResponse
    }
    Get(s"/blockflow?fromTs=10&toTs=0}") ~> server.route ~> check {
      status is StatusCodes.BadRequest
    }
  }

  it should "call GET /blocks/<hash>" in new RestServerFixture {
    Get(s"/blocks/${dummyBlockHeader.hash.toHexString}") ~> server.route ~> check {
      val chainIndex = ChainIndex.from(dummyBlockHeader.hash)
      if (config.brokerInfo.contains(chainIndex.from) || config.brokerInfo.contains(chainIndex.to)) {
        status is StatusCodes.OK
        responseAs[BlockEntry] is dummyBlockEntry
      } else {
        status is StatusCodes.BadRequest
      }
    }
  }

  it should "call GET /addresses/<address>/balance" in new RestServerFixture {
    Get(s"/addresses/$dummyKeyAddress/balance") ~> server.route ~> check {
      status is StatusCodes.OK
      responseAs[Balance] is dummyBalance
    }
  }

  it should "call GET /addresses/<address>/group" in new RestServerFixture {
    Get(s"/addresses/$dummyKeyAddress/group") ~> server.route ~> check {
      status is StatusCodes.OK
      responseAs[Group] is dummyGroup
    }
  }

  it should "call GET /hashes" in new RestServerFixture {
    Get(s"/hashes?fromGroup=1&toGroup=1&height=1") ~> server.route ~> check {
      status is StatusCodes.OK
      responseAs[HashesAtHeight] is dummyHashesAtHeight
    }
    Get(s"/hashes?toGroup=1&height=1") ~> server.route ~> check {
      status is StatusCodes.BadRequest
    }
    Get(s"/hashes?fromGroup=1&height=1") ~> server.route ~> check {
      status is StatusCodes.BadRequest
    }
    Get(s"/hashes?fromGroup=1&toGroup=1") ~> server.route ~> check {
      status is StatusCodes.BadRequest
    }
    Get(s"/hashes?fromGroup=10&toGroup=1&height=1") ~> server.route ~> check {
      status is StatusCodes.BadRequest
    }
    Get(s"/hashes?fromGroup=1&toGroup=10&height=1") ~> server.route ~> check {
      status is StatusCodes.BadRequest
    }
    Get(s"/hashes?fromGroup=1&toGroup=10&height=-1") ~> server.route ~> check {
      status is StatusCodes.BadRequest
    }
  }

  it should "call GET /chains" in new RestServerFixture {
    Get(s"/chains?fromGroup=1&toGroup=1") ~> server.route ~> check {
      status is StatusCodes.OK
      responseAs[ChainInfo] is dummyChainInfo
    }
    Get(s"/chains?toGroup=1") ~> server.route ~> check {
      status is StatusCodes.BadRequest
    }
    Get(s"/chains?fromGroup=1") ~> server.route ~> check {
      status is StatusCodes.BadRequest
    }
    Get(s"/chains?fromGroup=10&toGroup=1") ~> server.route ~> check {
      status is StatusCodes.BadRequest
    }
    Get(s"/chains?fromGroup=1&toGroup=10") ~> server.route ~> check {
      status is StatusCodes.BadRequest
    }
  }

  it should "call GET /unsigned-transactions" in new RestServerFixture {
    Get(s"/unsigned-transactions?fromKey=$dummyKey&toAddress=$dummyToAddres&value=1") ~> server.route ~> check {
      status is StatusCodes.OK
      responseAs[CreateTransactionResult] is dummyCreateTransactionResult
    }
  }

  it should "call POST /transactions" in new RestServerFixture {
    val tx =
      s"""{"tx":"${Hex.toHexString(serialize(dummyTx.unsigned))}","signature":"${dummySignature.toHexString}","publicKey":"$dummyKey"}"""
    val entity = HttpEntity(ContentTypes.`application/json`, tx)
    Post(s"/transactions", entity) ~> server.route ~> check {
      status is StatusCodes.OK
      responseAs[TxResult] is dummyTransferResult
    }
  }

  it should "call POST /miners" in new RestServerFixture {
    Post(s"/miners?action=start-mining") ~> server.route ~> check {
      status is StatusCodes.OK
      responseAs[Boolean] is true
      minerProbe.expectMsg(Miner.Start)
    }

    Post(s"/miners?action=stop-mining") ~> server.route ~> check {
      status is StatusCodes.OK
      responseAs[Boolean] is true
      minerProbe.expectMsg(Miner.Stop)
    }
  }

  trait RestServerFixture extends ServerFixture {

    val minerProbe = TestProbe()
    val miner      = ActorRefT[Miner.Command](minerProbe.ref)

    lazy val mode: Mode = new ModeDummy(dummyIntraCliqueInfo,
                                        dummyNeighborCliques,
                                        dummyBlock,
                                        TestProbe().ref,
                                        dummyTx,
                                        storages)
    lazy val server: RestServer = RestServer(mode, miner)
  }
}
