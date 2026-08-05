import { env } from '../config/env'
import type { ServiceContainer } from './contracts'
import { restServices } from './api/restServices'
import { mockServices } from './mock/mockServices'

const selectedServices = env.useMocks ? mockServices : restServices

export const services: ServiceContainer = {
  ...selectedServices,
  auth:env.useAuthMocks ? mockServices.auth : restServices.auth,
}
