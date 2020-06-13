package org.alephium.protocol.vm

import scala.annotation.tailrec

import org.alephium.util.AVector

final case class Runtime[Ctx <: Context](stack: Stack[Frame[Ctx]], var returnTo: Option[Val] = None)

trait VM[Ctx <: Context] {
  def execute(ctx: Ctx,
              script: Script[Ctx],
              fields: AVector[Val],
              args: AVector[Val]): ExeResult[Seq[Val]] = {
    val stack = Stack.ofCapacity[Frame[Ctx]](stackMaxSize)
    val rt    = Runtime[Ctx](stack)

    stack.push(script.startFrame(ctx, fields, args, value => Right(rt.returnTo = Some(value))))
    execute(stack).map(_ => rt.returnTo.toSeq)
  }

  @tailrec
  private def execute(stack: Stack[Frame[Ctx]]): ExeResult[Unit] = {
    if (!stack.isEmpty) {
      val currentFrame = stack.topUnsafe
      if (currentFrame.isComplete) {
        stack.pop()
        execute(stack)
      } else {
        currentFrame.execute()
      }
    } else {
      Right(())
    }
  }
}

object StatelessVM extends VM[StatelessContext]

object StatefulVM extends VM[StatefulContext]
