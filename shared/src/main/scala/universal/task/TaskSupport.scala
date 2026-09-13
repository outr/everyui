package universal.task

trait TaskSupport extends Updates:
  def start(task: Animation): TaskInstance =
    val instance = TaskInstance(task, this)
    instance.start()
    instance
