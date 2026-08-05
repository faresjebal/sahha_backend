import type { ApiProblem } from '../../models/api'

export class ApiError extends Error {
  readonly problem: ApiProblem

  constructor(problem: ApiProblem) {
    super(problem.detail || problem.title)
    this.name = 'ApiError'
    this.problem = problem
  }
}

export const apiErrorMessage = (error: unknown, fallback: string) => {
  if (error instanceof ApiError) {
    const fieldErrors = error.problem.fieldErrors || error.problem.errors
    const firstFieldError = fieldErrors
      ? Object.values(fieldErrors)[0]
      : undefined
    return firstFieldError || error.problem.detail || error.problem.title || fallback
  }
  if (error instanceof TypeError) {
    return 'Sahha could not reach API Gateway. Check that Discovery, Auth, and Gateway are running.'
  }
  return error instanceof Error && error.message ? error.message : fallback
}
