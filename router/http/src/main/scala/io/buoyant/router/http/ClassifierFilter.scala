package io.buoyant.router.http

import com.twitter.finagle.service.{ResponseClass, ResponseClassifier, ReqRep}
import com.twitter.finagle.{Service, ServiceFactory, SimpleFilter, Stack, Stackable, param}
import com.twitter.finagle.http.{Request, Response}
import com.twitter.util.{Future, Return, Try}

object ClassifierFilter {
  val role = Stack.Role("Classifier")

  val SuccessClassHeader = "l5d-success-class"

  def module: Stackable[ServiceFactory[Request, Response]] =
    new Stack.Module1[param.ResponseClassifier, ServiceFactory[Request, Response]] {
      val role = ClassifierFilter.role
      val description = "Sets the response classification into a header"
      def make(classifierP: param.ResponseClassifier, next: ServiceFactory[Request, Response]) = {
        val param.ResponseClassifier(classifier) = classifierP
        new ClassifierFilter(classifier).andThen(next)
      }
    }

  val successClassClassifier: ResponseClassifier = {
    case ReqRep(req, Return(rep: Response)) if rep.headerMap.contains(SuccessClassHeader) =>
      val success = Try(rep.headerMap(SuccessClassHeader).toDouble).getOrElse(0.0)
      if (success > 0.0)
        ResponseClass.Successful(success)
      else
        ResponseClass.Failed(false)
  }
}

class ClassifierFilter(classifier: ResponseClassifier) extends SimpleFilter[Request, Response] {

  private[this] val successClass = classifier.andThen(_.fractionalSuccess).lift

  def apply(req: Request, svc: Service[Request, Response]): Future[Response] = {
    svc(req).map { rep =>
      successClass(ReqRep(req, Return(rep))) match {
        case Some(success) =>
          rep.headerMap(ClassifierFilter.SuccessClassHeader) = success.toString
        case None =>
      }
      rep
    }
  }
}