module "lambda_update_service_list" {
  source = "git::https://github.com/wellcometrust/terraform.git//lambda?ref=v1.0.0"
  s3_key = "lambdas/monitoring/update_service_list.zip"

  name        = "update_service_list"
  description = "Publish ECS service status summary to S3"
  timeout     = 10

  environment_variables = {
    BUCKET_NAME     = "${aws_s3_bucket.dashboard.id}"
    OBJECT_KEY      = "data/ecs_status.json"
    ASSUMABLE_ROLES = "${join(",", var.dashboard_assumable_roles)}"
  }

  alarm_topic_arn = "${data.terraform_remote_state.lambdas.lambda_error_alarm_arn}"
}

module "trigger_update_service_list" {
  source = "git::https://github.com/wellcometrust/terraform.git//lambda/trigger_cloudwatch?ref=v1.0.0"

  lambda_function_name    = "${module.lambda_update_service_list.function_name}"
  lambda_function_arn     = "${module.lambda_update_service_list.arn}"
  cloudwatch_trigger_arn  = "${aws_cloudwatch_event_rule.every_minute.arn}"
  cloudwatch_trigger_name = "${aws_cloudwatch_event_rule.every_minute.name}"
}
