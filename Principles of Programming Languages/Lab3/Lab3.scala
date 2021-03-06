package jsy.student

import jsy.lab4.Lab4Like

object Lab4 extends jsy.util.JsyApplication with Lab4Like {
  import jsy.lab4.ast._
  import jsy.lab4.Parser
  
  /*
   * CSCI 3155: Lab 4
   * <Your Name>
   * 
   * Partner: <Your Partner's Name>
   * Collaborators: <Any Collaborators>
   */

  /*
   * Fill in the appropriate portions above by replacing things delimited
   * by '<'... '>'.
   * 
   * Replace the '???' expression with your code in each function.
   *
   * Do not make other modifications to this template, such as
   * - adding "extends App" or "extends Application" to your Lab object,
   * - adding a "main" method, and
   * - leaving any failing asserts.
   *
   * Your lab will not be graded if it does not compile.
   *
   * This template compiles without error. Before you submit comment out any
   * code that does not compile or causes a failing assert. Simply put in a
   * '???' as needed to get something that compiles without error. The '???'
   * is a Scala expression that throws the exception scala.NotImplementedError.
   */
  
  /*** Collections and Higher-Order Functions ***/
  
  /* Lists */
  
  def compressRec[A](l: List[A]): List[A] = l match {
    case Nil | _ :: Nil => l
    case h1 :: (t1 @ (h2 :: _)) => if(h1==h2) compressRec(t1) else h1::compressRec(t1) /*why h2*/
  }
  
  def compressFold[A](l: List[A]): List[A] = l.foldRight(Nil: List[A]){
    (h, acc) => acc match {
      case Nil => h::Nil
      case (h1::_) => if(h==h1) acc else h::acc
    }
  }
  
  def mapFirst[A](l: List[A])(f: A => Option[A]): List[A] = l match {
    case Nil => Nil
    case h::t => f(h) match {
      case Some(y) => y::t
      case _=> h::mapFirst(t)(f)
    }
  }

  
  /* Trees */

  def foldLeft[A](t: Tree)(z: A)(f: (A, Int) => A): A = {
    def loop(acc: A, t: Tree): A = t match {
      case Empty => acc
      case Node(l, d, r) => loop(f(loop(acc, l), d), r)
    }
    loop(z, t)
  }

  // An example use of foldLeft
  def sum(t: Tree): Int = foldLeft(t)(0){ (acc, d) => acc + d }

  // Create a tree from a list. An example use of the
  // List.foldLeft method.
  def treeFromList(l: List[Int]): Tree =
    l.foldLeft(Empty: Tree){ (acc, i) => acc insert i }

  def strictlyOrdered(t: Tree): Boolean = {
    val (b, _) = foldLeft(t)((true, None: Option[Int])){
      (acc, x) => acc match {
        case (b, None) => (b, Some(x))
        case (b, Some(e)) => if (e < x) (b, Some(e)) else (false, Some(e))
      }
    }
    b
  }

  /*** Rename bound variables in e ***/

  def rename(e: Expr)(fresh: String => String): Expr = {
    def ren(env: Map[String,String], e: Expr): Expr = {
      e match {
        case N(_) | B(_) | Undefined | S(_) => e
        case Print(e1) => Print(ren(env, e1))

        case Unary(uop, e1) => Unary(uop, ren(env, e1))
        case Binary(bop, e1, e2) => Binary(bop, ren(env, e1), ren(env, e2))
        case If(e1, e2, e3) => If(ren(env, e1), ren(env, e2), ren(env, e3))

        case Var(y) => if(env.contains(y)) Var(env(y)) else Var(y)

        case Decl(mode, y, e1, e2) => {
          val yp = fresh(y)
          val envp = extend(env, y, yp)
          Decl(mode, yp, ren(env, e1), ren(envp, e2))
        }

        case Function(p, params, tann, e1) => {
          val (pp, envp): (Option[String], Map[String,String]) = p match {
            case None => (None, env)
            case Some(x) => (Some(fresh(x)), extend(env, x, fresh(x)))
          }
          val (paramsp, envpp) = params.foldRight( (Nil: List[(String,MTyp)], envp) ) {
            case ((x,m), (paramsp, envpp)) => {
              val x2 = fresh(x)
              ((x2, m)::paramsp, extend(envpp, x, x2))

              }

          }
          Function(pp, paramsp, tann, ren(envpp, e1))
        }

        case Call(e1, args) => Call(ren(env, e1), args map {case (e1) => ren(env, e1)})
        case Obj(fields) => Obj(fields mapValues((e1) => ren(env, e1)))
        case GetField(e1, f) => GetField(ren(env, e1), f)
      }

    }
    ren(empty, e)
  }

  /*** Type Inference ***/

  // While this helper function is completely given, this function is
  // worth studying to see how library methods are used.
  def hasFunctionTyp(t: Typ): Boolean = t match {
    case TFunction(_, _) => true
    case TObj(fields) if (fields exists { case (_, t) => hasFunctionTyp(t) }) => true
    case _ => false
  }
  
  def typeof(env: TEnv, e: Expr): Typ = {
    def err[T](tgot: Typ, e1: Expr): T = throw StaticTypeError(tgot, e1, e)

    e match {
      case Print(e1) => typeof(env, e1); TUndefined
      case N(_) => TNumber
      case B(_) => TBool
      case Undefined => TUndefined
      case S(_) => TString
      case Var(x) => env(x)
      case Unary(Neg, e1) => typeof(env, e1) match {
        case TNumber => TNumber
        case tgot => err(tgot, e1)
      }
      case Unary(Not, e1) => typeof(env, e1) match {
        case TBool => TBool
        case t1 => err(t1, e1)
      }
      case Binary(Plus, e1, e2) => ((typeof(env, e1)), (typeof(env, e2))) match {
        case(TNumber, TNumber) => TNumber
        case(TString, TString) => TString
        case(TString, t2) => err(t2, e2)
        case(TNumber, t2) => err(t2, e2)
        case(t1, t2) => err(t1, e1)
      }

      case Binary(Minus|Times|Div, e1, e2) => ((typeof(env, e1)), (typeof(env, e2))) match {
        case(TNumber, TNumber) => TNumber
        case(TNumber, t2) => err(t2, e2)
        case(t1, t2) => err(t1, e1)
      }

      case Binary(Eq|Ne, e1, e2) => ((typeof(env, e1)), (typeof(env, e2))) match {
        case (TNumber, TNumber) => TBool
        case (TString, TString) => TBool
        case (TBool, TBool) => TBool
        case (TUndefined, TUndefined) => TBool
        case (t1, t2) => err(t1, e1)
      }

      case Binary(Lt|Le|Gt|Ge, e1, e2) => ((typeof(env, e1)), (typeof(env, e2))) match {
          case (TNumber, TNumber) => TBool
          case (TString, TString) => TBool
          case (TNumber, t2) => err(t2, e2)
          case (TString, t2) => err(t2, e2)
          case (t1, t2) => err(t1, e1)
      }
      case Binary(And|Or, e1, e2) => ((typeof(env, e1)), (typeof(env, e2))) match {
        case (TBool, TBool) => TBool
        case (TBool, t2) => err(t2, e2)
        case (t1, t2) => err(t1, e1)
      }

      case Binary(Seq, e1, e2) => typeof(env, e2) match {
        case TNumber => TNumber
        case TString => TString
        case TBool => TBool
        case TUndefined => TUndefined
        case t2 => err(t2, e2)
      }
      case If(e1, e2, e3) => (typeof(env, e1), typeof(env, e2), typeof(env, e3)) match {
        case (TBool, t2, t3) => if(t2==t3) t2 else err(t3, e3)
        case (t1,_,_) => err(t1, e1)
      }


      case Obj(fields) => TObj(fields mapValues(exp => typeof(env, exp)))
      case GetField(e1, f) => typeof(env, e1) match {
        case (TObj(fields)) => if(fields.contains(f)) fields(f) else throw err(typeof(env, e1), e1)
        case _ => err(typeof(env, e1), e1)
      }

      case Decl(m, x, e1, e2) => m match {
        case MConst => typeof(extend(env, x, typeof(env, e1)), e2)
        case MName => typeof(extend(env, x, typeof(env, e1)), e2)
      }

      case Function(p, params, tann, e1) => {
        // Bind to env1 an environment that extends env with an appropriate binding if
        // the function is potentially recursive.
        val env1 = (p, tann) match {
          case (Some(p), Some(tann)) => extend(env, p, TFunction(params, tann))
          case (Some(p), None) => err(TUndefined, e1)
          case (None, tann) => env
          case _ => err(TUndefined, e1)
        }

        val env2 = params.foldLeft(env1) {
          (env1: TEnv, i: (String, MTyp)) => {
            val m = i._1
            val n = i._2.t
            extend(env1, m, n)
          }
        }
        val t1 = typeof(env2, e1)

        tann match {
          case None => TFunction(params, t1)
          case Some(tp) => if(tp==t1) TFunction(params, t1) else err(tp, e1)
        }

      }

      case Call(e1, args) => typeof(env, e1) match {
        case TFunction(params, tret) if (params.length == args.length) =>
          (params zip args).foreach {
            case (param, arg) =>
              val m = param._2.t
              val n = typeof(env, arg)
              if(m!=n) err(m, e1)
          }
          tret
        case tgot => err(tgot, e1)
      }
    }
  }
  
  /*** Small-Step Interpreter ***/

  /*
   * Helper function that implements the semantics of inequality
   * operators Lt, Le, Gt, and Ge on values.
   *
   * We suggest a refactoring of code from Lab 2 to be able to
   * use this helper function in eval and step.
   *
   * This should the same code as from Lab 3.
   */

  def inequalityVal(bop: Bop, v1: Expr, v2: Expr): Boolean = {
    require(isValue(v1), s"inequalityVal: v1 ${v1} is not a value")
    require(isValue(v2), s"inequalityVal: v2 ${v2} is not a value")
    require(bop == Lt || bop == Le || bop == Gt || bop == Ge)
    (v1, v2) match {
      case (S(v1), S(v2)) => bop match {
        case Lt =>  (v1) < (v2)
        case Le => (v1) <= (v2)
        case Gt => (v1) > (v2)
        case Ge => (v1) >= (v2)
      }
      case (N(v1), N(v2)) => bop match {
        case Lt => (v1) < (v2)
        case Le => (v1) <= (v2)
        case Gt => (v1) > (v2)
        case Ge => (v1) >= (v2)
      }
    }
  }

  /* This should be the same code as from Lab 3 */
  def iterate(e0: Expr)(next: (Expr, Int) => Option[Expr]): Expr = {
    def loop(e: Expr, n: Int): Expr = next(e, n) match {
      case None => e
      case Some(e2) => loop(e2, n + 1)
    }
    loop(e0, 0)
  }


  /* Capture-avoiding substitution in e replacing variables x with esub. */
  /* x is variable name, e is expression is being substituted into, esub is what is being substituted in*/
  def substitute(e: Expr, esub: Expr, x: String): Expr = {
    def subst(e: Expr): Expr = e match {
      case N(_) | B(_) | Undefined | S(_) => e
      case Print(e1) => Print(substitute(e1, esub, x))
        /***** Cases from Lab 3 */
      case Unary(uop, e1) => Unary(uop, substitute(e1, esub, x))
      case Binary(bop, e1, e2) => Binary(bop, substitute(e1, esub, x), substitute(e2, esub, x))
      case If(e1, e2, e3) => If(substitute(e1, esub, x), substitute(e2, esub, x), substitute(e3, esub, x))
      case Var(y) => if(x == y) esub else e
      case Decl(m, y, e1, e2) => if(x == y) Decl(m, y, substitute(e1, esub, x), e2) else Decl(m, y, substitute(e1, esub, x), substitute(e2, esub, x))

          /***** Cases needing adapting from Lab 3 */
      case Function(None, params, tann, e1) =>
        val result=(params.exists(z=> {z._1 ==x}));
        if(result) e else Function(None, params, tann, substitute(e1, esub, x))
      case Function(Some(y), params, tann, e1) =>
        val result2=(params.exists(z=> {z._1 ==x}))
        if(result2 | (x==y)) e else Function(Some(y), params, tann, substitute(e1, esub, x))
      case Call(e1, args) => Call(substitute(e1, esub, x), args.map(z => substitute(z, esub, x)))
        /***** New cases for Lab 4 */
      case Obj(fields) => Obj(fields.mapValues(x=> subst(x)))
      case GetField(e1, f) => GetField(subst(e1), f)
    }

    val fvs = freeVars(esub) /*returns list of strings*/
    def fresh(x: String): String = if (fvs.contains(x)) fresh(x + "$") else x

    subst(rename(e)(fresh)) // change this line when you implement capture-avoidance
  }

  /* Check whether or not an expression is reducible given a mode. */
  def isRedex(mode: Mode, e: Expr): Boolean = mode match {
    case MConst => !isValue(e)
    case MName => false
  }

  /* A small-step transition. */
  def step(e: Expr): Expr = {
    require(!isValue(e), s"step: e ${e} to step is a value")
    e match {
      /* Base Cases: Do Rules */
      case Print(v1) if isValue(v1) => println(pretty(v1)); Undefined
        /***** Cases needing adapting from Lab 3. */
      case N(_) | B(_) | Undefined | S(_) | Function(_, _, _, _) => e

      case Unary(Neg, N(n))  => N(-(n))
      case Unary(Not, B(b)) => B(!(b))

      case Binary(Seq, v1, e2) if isValue(v1) => e2

      case Binary(Plus, v1, v2) => (v1, v2) match {
        case (S(v1), S(v2)) => S(v1+v2)
        case (N(v1), N(v2))=> N(v1+v2)
      }
      case Binary(Minus, N(n1), N(n2))  => N(n1-n2)
      case Binary(Times, N(n1), N(n2)) => N(n1 * n2)
      case Binary(Div, N(n1), N(n2)) => N(n1/n2)

      case Binary(Lt, N(n1), N(n2))  => B(inequalityVal(Lt, N(n1), N(n2)))
      case Binary(Lt, S(s1), S(s2))  => B(inequalityVal(Lt, S(s1), S(s2)))
      case Binary(Gt, N(n1), N(n2))  => B(inequalityVal(Gt, N(n1), N(n2)))
      case Binary(Gt, S(s1), S(s2))  => B(inequalityVal(Gt, S(s1), S(s2)))
      case Binary(Le, N(n1), N(n2))  => B(inequalityVal(Le, N(n1), N(n2)))
      case Binary(Le, S(s1), S(s2))  => B(inequalityVal(Le, S(s1), S(s2)))
      case Binary(Ge, N(n1), N(n2))  => B(inequalityVal(Ge, N(n1), N(n2)))
      case Binary(Ge, S(s1), S(s2))  => B(inequalityVal(Ge, S(s1), S(s2)))

      case Binary(Eq, v1, v2) if isValue(v1) && isValue(v2) => (v1, v2) match {
        case (v1, v2) => B(v1 == v2)
      }
      case Binary(Ne, v1, v2) if isValue(v1) && isValue(v2) => (v1, v2) match {
        case (v1, v2) => B(v1 != v2)
      }

      case Binary(And, B(b1), e2) => b1 match {
        case (true) => e2
        case (false) => B(false)
      }
      case Binary(Or, B(b1), e2) => b1 match {
        case (true) => B(true)
        case (false) => e2
      }
      case If(B(b1), e2, e3) => b1 match {
        case (true) => e2
        case (false) => e3
      }
      case Decl(m, x, e1, e2) => m match {
        case MConst => if (isValue(e1)) substitute(e2, e1, x) else Decl(m, x, step(e1), e2)
        case MName => substitute(e2, e1, x)
      }
      /***** More cases here */
      case Call(v1, args) if isValue(v1) =>
        v1 match {
          case Function(p, params, _, e1) => {
            val pazip = params zip args
            if (pazip.forall{case((_, MTyp(m, _)), arg) => !isRedex(m, arg)}) {//if reducible expressions
              val e1p = pazip.foldRight(e1) { //Body expr to get e1' - substitute into it
                case (((x, _), arg), e1) => substitute(e1, arg, x)
              }
              p match {//match on function name
                case None => e1p
                case Some(x1) => substitute(e1p, v1, x1)
              }
            }
            else {
              val pazipp = mapFirst(pazip) {
                case ((s, MTyp(mode, typ)), arg) => if (isRedex(mode, arg)) Some((s, MTyp(mode, typ)), step(arg)) else None
              }
              val (params, args) = pazipp.unzip
              Call(v1, args)
            }
          }
          case _ => throw StuckError(e)
        }

        case GetField(Obj(fields), f) if(isValue(Obj(fields))) => lookup(fields, f)
        /***** New cases for Lab 4. */

      /* Inductive Cases: Search Rules */
      case Print(e1) => Print(step(e1))
        /***** Cases from Lab 3. */
      case Unary(uop, e1) => Unary(uop, step(e1))

      case Binary(bop, e1, e2) if !isValue(e1) => Binary(bop, step(e1), e2)
      case Binary(bop, v1, e2) if isValue(v1) => Binary(bop, v1, step(e2))
      case If(e1, e2, e3) => If(step(e1), e2, e3)
      case Call(e1, args) => Call(step(e1), args)
        /***** New cases for Lab 4. */

      case Obj(fields) if !isValue(Obj(fields)) => {
        var f = mapFirst(fields.toList){

        case(str,expr) if(!isValue(expr)) => Some(str, step(expr))
        }
        Obj(f.toMap)
      }
      /* SearchGetField */
      case GetField(e1, f) => GetField(step(e1), f)
      case _ => throw StuckError(e)
    }
  }
  
  
  /* External Interfaces */
  
  //this.debug = true // uncomment this if you want to print debugging information
  this.maxSteps = Some(1000) // comment this out or set to None to not bound the number of steps.
  this.keepGoing = true // comment this out if you want to stop at first exception when processing a file
}
