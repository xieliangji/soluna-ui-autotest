export type SupportedTool = 'adb' | 'go-ios' | 'ios'

export interface CommandExecuteRequest {
  tool: SupportedTool
  args?: string[]
  timeoutMs?: number
  maxOutputBytes?: number
}

export interface CommandExecuteResult {
  command: string
  args: string[]
  exitCode: number | null
  timedOut: boolean
  truncated: boolean
  durationMs: number
  stdout: string
  stderr: string
}
