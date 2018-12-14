package upperbound

import cats.syntax.all._
import cats.effect._, concurrent.Ref
import scala.concurrent.duration._

import syntax.rate._

class SubmissionSemanticsSpec extends BaseSpec {
  import DefaultEnv._

  "A worker" - {

    "when using fire-and-forget semantics should" - {

      "continue execution immediately" in {
        def prog =
          for {
            complete <- Ref.of[IO, Boolean](false)
            limiter <- Limiter.start[IO](1 every 10.seconds)
            _ <- limiter submit complete.set(true)
            //_ <- limiter.shutDown TODO
            res <- complete.get
          } yield res

        val res = prog.unsafeRunSync

        assert(res === false)
      }
    }

    "when using await semantics should" - {

      "complete when the result of the submitted job is ready" in {
        def prog =
          for {
            complete <- Ref.of[IO, Boolean](false)
            limiter <- Limiter.start[IO](1 every 1.seconds)
            res <- limiter await complete.set(true).as("done")
            //_ <- limiter.shutDown TODO
            state <- complete.get
          } yield res -> state

        val (res, state) = prog.unsafeRunSync

        assert(res === "done")
        assert(state === true)
      }

      "report the original error if execution of the submitted job fails" in {
        case class MyError() extends Exception
        def prog =
          for {
            limiter <- Limiter.start[IO](1 every 1.seconds)
            res <- limiter await IO.raiseError[Int](new MyError)
            //_ <- limiter.shutDown TODO
          } yield res

        assertThrows[MyError](prog.unsafeRunSync)
      }
    }

    "when too many jobs have been submitted should" - {
      "reject new jobs immediately" in {
        def prog =
          for {
            limiter <- Limiter.start[IO](1 every 10.seconds, n = 0)
            res <- limiter await IO.unit
            //_ <- limiter.shutDown TODO
          } yield res

        def prog2 =
          for {
            limiter <- Limiter.start[IO](1 every 10.seconds, n = 0)
            _ <- limiter submit IO.unit
            // _ <- limiter.shutDown TODO
          } yield ()

        assertThrows[LimitReachedException](prog.unsafeRunSync)
        assertThrows[LimitReachedException](prog2.unsafeRunSync)
      }
    }
  }

}
