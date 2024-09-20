/*
    Contingency, version [unreleased]. Copyright 2024 Jon Pretty, Propensive OÜ.

    The primary distribution site is: https://propensive.com/

    Licensed under the Apache License, Version 2.0 (the "License"); you may not use this
    file except in compliance with the License. You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software distributed under the
    License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
    either express or implied. See the License for the specific language governing permissions
    and limitations under the License.
*/

package contingency

import language.experimental.pureFunctions

import fulminate.*
import rudiments.*

@capability
trait Tactic[-ErrorType <: Exception]:
  private inline def tactic: this.type = this

  def diagnostics: Diagnostics
  def record(error: Diagnostics ?=> ErrorType): Unit
  def abort(error: Diagnostics ?=> ErrorType): Nothing

  def contramap[ErrorType2 <: Exception](lambda: ErrorType2 => ErrorType): Tactic[ErrorType2] =
    new Tactic[ErrorType2]:
      def diagnostics: Diagnostics = tactic.diagnostics
      def record(error: Diagnostics ?=> ErrorType2): Unit = tactic.record(lambda(error))
      def abort(error: Diagnostics ?=> ErrorType2): Nothing = tactic.abort(lambda(error))
