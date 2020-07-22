package org.alephium.protocol.model

import akka.util.ByteString

import org.alephium.crypto._
import org.alephium.protocol.ALF
import org.alephium.protocol.config.GroupConfig
import org.alephium.protocol.vm.LockupScript
import org.alephium.serde.Serde
import org.alephium.util.{AVector, U64}

final case class Transaction(unsigned: UnsignedTransaction,
                             generatedOutputs: AVector[TxOutput],
                             signatures: AVector[ED25519Signature])
    extends ALF.HashSerde[Transaction] {
  override val hash: ALF.Hash = unsigned.hash

  // TODO: make these two functions safe
  def fromGroup(implicit config: GroupConfig): GroupIndex = {
    assume(unsigned.inputs.nonEmpty)
    unsigned.inputs.head.fromGroup
  }
  def toGroup(implicit config: GroupConfig): GroupIndex = {
    assume(unsigned.fixedOutputs.nonEmpty)
    unsigned.fixedOutputs.head match {
      case output: AssetOutput => output.toGroup
      case _: ContractOutput   => fromGroup
    }
  }
  def chainIndex(implicit config: GroupConfig): ChainIndex = ChainIndex(fromGroup, toGroup)

  def outputsLength: Int = unsigned.fixedOutputs.length + generatedOutputs.length

  def getOutput(index: Int): TxOutput = {
    assume(index >= 0 && index < outputsLength)
    if (index < unsigned.fixedOutputs.length) {
      unsigned.fixedOutputs(index)
    } else {
      generatedOutputs(index - unsigned.fixedOutputs.length)
    }
  }

  def alfAmountInOutputs: Option[U64] = {
    val sum1Opt =
      unsigned.fixedOutputs
        .foldE(U64.Zero)((sum, output) => sum.add(output.amount).toRight(()))
        .toOption
    val sum2Opt =
      generatedOutputs
        .foldE(U64.Zero)((sum, output) => sum.add(output.amount).toRight(()))
        .toOption
    for {
      sum1 <- sum1Opt
      sum2 <- sum2Opt
      sum  <- sum1.add(sum2)
    } yield sum
  }
}

object Transaction {
  implicit val serde: Serde[Transaction] =
    Serde.forProduct3(Transaction.apply, t => (t.unsigned, t.generatedOutputs, t.signatures))

  def from(inputs: AVector[TxInput],
           outputs: AVector[TxOutput],
           generatedOutputs: AVector[TxOutput],
           privateKey: ED25519PrivateKey): Transaction = {
    from(UnsignedTransaction(inputs, outputs), generatedOutputs, privateKey)
  }

  def from(inputs: AVector[TxInput],
           outputs: AVector[TxOutput],
           privateKey: ED25519PrivateKey): Transaction = {
    from(inputs, outputs, AVector.empty, privateKey)
  }

  def from(inputs: AVector[TxInput],
           outputs: AVector[TxOutput],
           signatures: AVector[ED25519Signature]): Transaction = {
    Transaction(UnsignedTransaction(inputs, outputs), generatedOutputs = AVector.empty, signatures)
  }

  def from(inputs: AVector[TxInput],
           outputs: AVector[TxOutput],
           generatedOutputs: AVector[TxOutput],
           signatures: AVector[ED25519Signature]): Transaction = {
    Transaction(UnsignedTransaction(inputs, outputs), generatedOutputs, signatures)
  }

  def from(unsigned: UnsignedTransaction, privateKey: ED25519PrivateKey): Transaction = {
    from(unsigned, AVector.empty, privateKey)
  }

  def from(unsigned: UnsignedTransaction,
           generatedOutputs: AVector[TxOutput],
           privateKey: ED25519PrivateKey): Transaction = {
    val inputCnt  = unsigned.inputs.length
    val signature = ED25519.sign(unsigned.hash.bytes, privateKey)
    Transaction(unsigned, generatedOutputs, AVector.fill(inputCnt)(signature))
  }

  def from(unsigned: UnsignedTransaction, signatures: AVector[ED25519Signature]): Transaction = {
    Transaction(unsigned, AVector.empty, signatures)
  }

  def coinbase(publicKey: ED25519PublicKey, height: Int, data: ByteString): Transaction = {
    val pkScript = LockupScript.p2pkh(publicKey)
    val txOutput = AssetOutput(ALF.CoinBaseValue, tokens = AVector.empty, height, pkScript, data)
    val unsigned = UnsignedTransaction(AVector.empty, AVector(txOutput))
    Transaction(unsigned, generatedOutputs = AVector.empty, signatures = AVector.empty)
  }

  def genesis(balances: AVector[(LockupScript, U64)]): Transaction = {
    val outputs = balances.map[TxOutput] {
      case (lockupScript, value) =>
        TxOutput.genesis(value, lockupScript)
    }
    val unsigned = UnsignedTransaction(inputs = AVector.empty, fixedOutputs = outputs)
    Transaction(unsigned, generatedOutputs = AVector.empty, signatures = AVector.empty)
  }
}
