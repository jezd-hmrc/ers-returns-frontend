/*
 * Copyright 2019 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import play.api.Logger

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

package object controllers {
  case class LoopException[A](retryNumber: Int, finalCacheList: Option[A])
    extends Exception(s"Failed to meet predicate after retrying ${retryNumber} times")

  implicit class RetryCache[A](f: => Future[A]) {
    def withRetry(maxTimes: Int)(pToBreakLoop: A => Boolean): Future[A] = {
      def loop(count: Int = 0, previous: Option[A] = None): Future[A] = {
        Logger.info(s"Retrying call x$count")
        if(count < maxTimes){
          f.flatMap {
            csvFileList =>
              if(pToBreakLoop(csvFileList)) {
                Future.successful(csvFileList)
              } else loop(count + 1, previous)
          }
        } else throw LoopException(count, previous)
      }
      loop()
    }
  }
}
