export const DEMO_COOKIES = Object.freeze({ access:'SAHHA_DEMO_ACCESS', refresh:'SAHHA_DEMO_REFRESH', device:'SAHHA_DEMO_DEVICE', csrf:'SAHHA_DEMO_XSRF' })
export function isolatedEnvironment(source = process.env) {
  const allowed = /^(PATH|SYSTEMROOT|WINDIR|SYSTEMDRIVE|COMSPEC|PATHEXT|TEMP|TMP|USERPROFILE|APPDATA|LOCALAPPDATA|PROGRAMDATA|PROCESSOR_ARCHITECTURE|NUMBER_OF_PROCESSORS)$/i
  return { ...Object.fromEntries(Object.entries(source).filter(([key]) => allowed.test(key))),
    AUTH_ACCESS_COOKIE_NAME:DEMO_COOKIES.access, AUTH_REFRESH_COOKIE_NAME:DEMO_COOKIES.refresh,
    AUTH_DEVICE_COOKIE_NAME:DEMO_COOKIES.device, AUTH_CSRF_COOKIE_NAME:DEMO_COOKIES.csrf,
    AUTH_JWT_EPHEMERAL_KEY_ENABLED:'true', AUTH_COOKIE_SECURE:'false', AUTH_MAIL_ENABLED:'false',
    AUTH_SESSION_CACHE_ENABLED:'false', AUTH_REDIS_PORT:'16379', REDIS_PORT:'16379',
    CONFIG_CLIENT_ENABLED:'false', EUREKA_URL:'http://127.0.0.1:8761/eureka/',
    AUTH_JWK_SET_URI:'http://127.0.0.1:8081/.well-known/jwks.json',
    AUTH_SESSION_CHECK_URI:'http://127.0.0.1:8081/api/v1/internal/auth/session-check',
    FRONTEND_ALLOWED_ORIGINS:'http://127.0.0.1:5173' }
}

const PUBLISHERS = Object.freeze({auth:'AUTH_OUTBOX_PUBLISHER_ENABLED',organisation:'ORGANISATION_OUTBOX_PUBLISHER_ENABLED',patient:'PATIENT_OUTBOX_PUBLISHER_ENABLED',scheduling:'SCHEDULING_OUTBOX_PUBLISHER_ENABLED',clinical:'CLINICAL_OUTBOX_PUBLISHER_ENABLED',communication:'COMMUNICATION_OUTBOX_PUBLISHER_ENABLED',file:'FILE_OUTBOX_PUBLISHER_ENABLED'})
export function serviceEnvironment(name,runtime = {},source = process.env) {
  const env = isolatedEnvironment(source)
  if (!runtime.platform) return env
  if (!/^[a-f0-9]{64}$/.test(runtime.redisPassword ?? '') || !/^[a-f0-9]{64}$/.test(runtime.hmacSecret ?? '')) throw new Error('Missing generation-scoped application secrets')
  const service = name.replace('-service','')
  if (!['discovery-server','config-server'].includes(name)) {
    env.CONFIG_CLIENT_ENABLED='true'; env.CONFIG_SERVER_URL='http://127.0.0.1:8888'
  }
  if (['auth','communication'].includes(service)) {
    env.SPRING_DATA_REDIS_HOST='127.0.0.1'; env.SPRING_DATA_REDIS_PORT='16379'; env.SPRING_DATA_REDIS_PASSWORD=runtime.redisPassword
    if (service === 'auth') { env.AUTH_SESSION_CACHE_ENABLED='true'; env.AUTH_REDIS_PASSWORD=runtime.redisPassword }
  }
  if (service === 'patient') env.PATIENT_IDENTIFIER_HMAC_SECRET=runtime.hmacSecret
  if (service === 'file') {
    const storage = runtime.storage
    if (storage?.endpoint !== 'http://127.0.0.1:18333' || storage.bucket !== 'sahha-synthetic-files'
      || !/^[a-f0-9]{32}$/.test(storage.accessKey ?? '') || !/^[a-f0-9]{64}$/.test(storage.secretKey ?? '')) throw new Error('Missing isolated storage settings')
    Object.assign(env,{SEAWEEDFS_S3_ENDPOINT:storage.endpoint,SEAWEEDFS_FILE_BUCKET:storage.bucket,SEAWEEDFS_S3_ACCESS_KEY:storage.accessKey,SEAWEEDFS_S3_SECRET_KEY:storage.secretKey,
      FILE_SYNTHETIC_CLEAN_ENABLED:runtime.syntheticScan === true ? 'true' : 'false'})
  }
  // Even disabled clients cannot accidentally fall back to the normal broker.
  env.KAFKA_BOOTSTRAP_SERVERS='127.0.0.1:19092'
  if (runtime.eventDelivery === true) {
    if (PUBLISHERS[service]) env[PUBLISHERS[service]]='true'
    if (service === 'scheduling') env.SCHEDULING_CLINICAL_CONSUMER_ENABLED='true'
    if (service === 'notification') Object.assign(env,{NOTIFICATION_APPOINTMENT_CONSUMER_ENABLED:'true',NOTIFICATION_COMMUNICATION_CONSUMER_ENABLED:'true',NOTIFICATION_REFERRAL_CONSUMER_ENABLED:'true'})
  }
  return env
}
