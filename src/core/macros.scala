/*
    Perforate, version [unreleased]. Copyright 2023 Jon Pretty, Propensive OÜ.

    The primary distribution site is: https://propensive.com/

    Licensed under the Apache License, Version 2.0 (the "License"); you may not use this
    file except in compliance with the License. You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software distributed under the
    License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
    either express or implied. See the License for the specific language governing permissions
    and limitations under the License.
*/

package perforate

import fulminate.*

import scala.quoted.*
import scala.compiletime.*

object Perforate:
  def union(using quotes: Quotes)(repr: quotes.reflect.TypeRepr): Set[quotes.reflect.TypeRepr] =
    import quotes.reflect.*
    repr.dealias.asMatchable match
      case OrType(left, right) => union(left) ++ union(right)
      case other               => Set(other)

  def mitigate
      [ErrorType <: Error: Type, SuccessType: Type]
      (mitigation: Expr[Mitigation[SuccessType, ErrorType]], handler: Expr[PartialFunction[ErrorType, Error]])
      (using Quotes)
      : Expr[Any] =
    import quotes.reflect.*

    val resultTypes = handler.asTerm match
      case Inlined(_, _, Block(List(DefDef(_, _, _, Some(Match(matchId, caseDefs)))), term)) =>
        def recur(done: Set[TypeRepr], original: Set[TypeRepr], caseDefs: List[CaseDef]): Set[TypeRepr] = caseDefs match
          case Nil =>
            done ++ original
          
          case caseDef :: todo => caseDef match
            case CaseDef(pattern, None, rhs) => rhs.asExpr match
              case '{type rhsType <: Error; $expr: rhsType} =>
                pattern match
                  case Unapply(Select(ident, "unapply"), _, _) =>
                    original.find(_.typeSymbol == ident.symbol.companionClass.typeRef.typeSymbol) match
                      case None      => recur(done, original, todo)
                      case Some(ref) => recur(done + TypeRepr.of[rhsType], original - ref, todo)
                  case Typed(_, ident) =>
                    original.find(_.typeSymbol == ident.symbol.typeRef.typeSymbol) match
                      case None      => recur(done, original, todo)
                      case Some(ref) => recur(done + TypeRepr.of[rhsType], original - ref, todo)
                  case Bind(_, ident) =>
                    original.find(_.typeSymbol == ident.symbol.typeRef.typeSymbol) match
                      case None      => recur(done, original, todo)
                      case Some(ref) => recur(done + TypeRepr.of[rhsType], original - ref, todo)
                  case _ =>
                    recur(done + TypeRepr.of[rhsType], original, todo)
              
        recur(Set(), union(TypeRepr.of[ErrorType]), caseDefs)

    resultTypes.foldLeft(TypeRepr.of[Nothing]): (acc, next) =>
      acc.asType match
        case '[type acc <: Error; acc] => next.asType match
          case '[type next <: Error; next] => TypeRepr.of[acc | next]
    .asType match
      case '[type errorType <: Error; errorType] =>
        '{$mitigation.handle($handler).asInstanceOf[Mitigation[SuccessType, errorType]]}
  