import io
import json
import unittest
from urllib.error import HTTPError, URLError
from unittest.mock import patch, MagicMock

from soluna_client import (
    DeviceInfo,
    LogSessionCreateResult,
    LogSessionDeleteResult,
    LogSessionReadResult,
    SolunaAPIError,
    SolunaClient,
    SolunaClientError,
    SolunaHTTPError,
)


class _FakeResponse:
    def __init__(self, payload):
        self._payload = json.dumps(payload).encode('utf-8')

    def read(self):
        return self._payload

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        return False


class SolunaClientTests(unittest.TestCase):
    def setUp(self):
        self.client = SolunaClient('http://127.0.0.1:4723')

    @patch('soluna_client.request.urlopen')
    def test_get_device_success(self, mock_urlopen):
        mock_urlopen.return_value = _FakeResponse(
            {
                'value': {
                    'exists': True,
                    'device': {
                        'platform': 'android',
                        'udid': 'abc123',
                        'name': 'Pixel 8',
                        'model': 'Pixel 8',
                        'osVersion': '14',
                    },
                }
            }
        )

        with patch.object(self.client._opener, 'open', return_value=mock_urlopen.return_value):
            result = self.client.get_device('abc123')
        self.assertTrue(result.exists)
        self.assertIsNotNone(result.device)
        assert result.device is not None
        self.assertEqual(result.device.udid, 'abc123')
        self.assertEqual(result.device.os_version, '14')

    @patch('soluna_client.request.urlopen')
    def test_get_device_not_found(self, mock_urlopen):
        mock_urlopen.return_value = _FakeResponse(
            {'value': {'exists': False, 'message': "Device 'missing' not found on this host"}}
        )

        http_error = HTTPError(
            url='http://127.0.0.1:4723/soluna/device?udid=missing',
            code=404,
            msg='Not Found',
            hdrs=None,
            fp=io.BytesIO(json.dumps({'value': {'exists': False, 'message': "Device 'missing' not found on this host"}}).encode('utf-8')),
        )
        with patch.object(self.client._opener, 'open', side_effect=http_error):
            result = self.client.get_device('missing')
        self.assertFalse(result.exists)
        self.assertIsNone(result.device)
        self.assertIn('not found', result.message or '')

    @patch('soluna_client.request.urlopen')
    def test_list_devices_success(self, mock_urlopen):
        mock_urlopen.return_value = _FakeResponse(
            {
                'value': {
                    'count': 2,
                    'devices': [
                        {
                            'platform': 'android',
                            'udid': 'abc123',
                            'name': 'Pixel 8',
                            'model': 'Pixel 8',
                            'osVersion': '14',
                        },
                        {
                            'platform': 'ios',
                            'udid': 'ios-1',
                            'name': 'iPhone 15',
                            'model': 'iPhone15,4',
                            'osVersion': '17.5',
                        },
                    ],
                }
            }
        )

        with patch.object(self.client._opener, 'open', return_value=mock_urlopen.return_value):
            result = self.client.list_devices()
        self.assertEqual(result.count, 2)
        self.assertEqual(len(result.devices), 2)
        self.assertIsInstance(result.devices[0], DeviceInfo)
        self.assertEqual(result.devices[1].platform, 'ios')

    @patch('soluna_client.request.urlopen')
    def test_get_device_info_alias(self, mock_urlopen):
        mock_urlopen.return_value = _FakeResponse(
            {
                'value': {
                    'exists': True,
                    'device': {
                        'platform': 'android',
                        'udid': 'abc123',
                        'name': 'Pixel 8',
                        'model': 'Pixel 8',
                        'osVersion': '14',
                    },
                }
            }
        )

        with patch.object(self.client._opener, 'open', return_value=mock_urlopen.return_value):
            result = self.client.get_device_info('abc123')
        self.assertTrue(result.exists)
        assert result.device is not None
        self.assertEqual(result.device.udid, 'abc123')

    @patch('soluna_client.request.urlopen')
    def test_execute_command_success(self, mock_urlopen):
        mock_urlopen.return_value = _FakeResponse(
            {
                'value': {
                    'ok': True,
                    'command': 'adb',
                    'args': ['devices'],
                    'exitCode': 0,
                    'timedOut': False,
                    'truncated': False,
                    'durationMs': 13,
                    'stdout': 'List of devices attached\n',
                    'stderr': '',
                }
            }
        )

        with patch.object(self.client._opener, 'open', return_value=mock_urlopen.return_value):
            result = self.client.execute_command('adb', ['devices'], timeout_ms=1000, max_output_bytes=4096)
        self.assertTrue(result.ok)
        self.assertEqual(result.exit_code, 0)
        self.assertEqual(result.command, 'adb')

    @patch('soluna_client.request.urlopen')
    def test_create_log_session_success(self, mock_urlopen):
        mock_urlopen.return_value = _FakeResponse(
            {
                'value': {
                    'session': {
                        'sessionId': 's-1',
                        'udid': 'abc123',
                        'platform': 'android',
                        'status': 'running',
                        'command': 'adb',
                        'args': ['-s', 'abc123', 'logcat', '-v', 'threadtime'],
                        'startedAt': '2026-03-25T07:00:00.000Z',
                        'lastActivityAt': '2026-03-25T07:00:00.000Z',
                        'ttlMs': 600000,
                        'nextSeq': 0,
                        'minSeq': 0,
                        'droppedCount': 0,
                        'maxBufferEntries': 1000,
                        'maxSessionBytes': 104857600,
                    }
                }
            }
        )

        with patch.object(self.client._opener, 'open', return_value=mock_urlopen.return_value):
            result = self.client.create_log_session('abc123')

        self.assertIsInstance(result, LogSessionCreateResult)
        self.assertEqual(result.session.session_id, 's-1')
        self.assertEqual(result.session.platform, 'android')

    @patch('soluna_client.request.urlopen')
    def test_read_log_session_success(self, mock_urlopen):
        mock_urlopen.return_value = _FakeResponse(
            {
                'value': {
                    'session': {
                        'sessionId': 's-1',
                        'udid': 'abc123',
                        'platform': 'android',
                        'status': 'running',
                        'command': 'adb',
                        'args': ['-s', 'abc123', 'logcat', '-v', 'threadtime'],
                        'startedAt': '2026-03-25T07:00:00.000Z',
                        'lastActivityAt': '2026-03-25T07:00:10.000Z',
                        'ttlMs': 600000,
                        'nextSeq': 2,
                        'minSeq': 0,
                        'droppedCount': 0,
                        'maxBufferEntries': 1000,
                        'maxSessionBytes': 104857600,
                    },
                    'cursor': 0,
                    'nextCursor': 1,
                    'cursorAdjusted': False,
                    'entries': [
                        {
                            'seq': 0,
                            'ts': '2026-03-25T07:00:01.000Z',
                            'platform': 'android',
                            'udid': 'abc123',
                            'source': 'stdout',
                            'message': 'hello',
                            'raw': 'hello',
                            'tag': 'Tag',
                            'pid': 123,
                        }
                    ],
                }
            }
        )

        with patch.object(self.client._opener, 'open', return_value=mock_urlopen.return_value):
            result = self.client.read_log_session('s-1', cursor=0, limit=100)

        self.assertIsInstance(result, LogSessionReadResult)
        self.assertEqual(result.next_cursor, 1)
        self.assertEqual(len(result.entries), 1)
        self.assertEqual(result.entries[0].message, 'hello')
        self.assertEqual(result.entries[0].pid, 123)

    @patch('soluna_client.request.urlopen')
    def test_delete_log_session_success(self, mock_urlopen):
        mock_urlopen.return_value = _FakeResponse(
            {
                'value': {
                    'sessionId': 's-1',
                    'removed': True,
                }
            }
        )

        with patch.object(self.client._opener, 'open', return_value=mock_urlopen.return_value):
            result = self.client.delete_log_session('s-1')

        self.assertIsInstance(result, LogSessionDeleteResult)
        self.assertEqual(result.session_id, 's-1')
        self.assertTrue(result.removed)

    @patch('soluna_client.request.urlopen')
    def test_log_session_context_auto_cleanup(self, mock_urlopen):
        create_resp = _FakeResponse(
            {
                'value': {
                    'session': {
                        'sessionId': 's-ctx',
                        'udid': 'abc123',
                        'platform': 'android',
                        'status': 'running',
                        'command': 'adb',
                        'args': ['-s', 'abc123', 'logcat', '-v', 'threadtime'],
                        'startedAt': '2026-03-25T07:00:00.000Z',
                        'lastActivityAt': '2026-03-25T07:00:00.000Z',
                        'ttlMs': 600000,
                        'nextSeq': 0,
                        'minSeq': 0,
                        'droppedCount': 0,
                        'maxBufferEntries': 1000,
                        'maxSessionBytes': 104857600,
                    }
                }
            }
        )
        delete_resp = _FakeResponse({'value': {'sessionId': 's-ctx', 'removed': True}})

        with patch.object(self.client._opener, 'open', side_effect=[create_resp, delete_resp]) as mocked_open:
            with self.client.log_session('abc123') as (session_id, created):
                self.assertEqual(session_id, 's-ctx')
                self.assertEqual(created.session.udid, 'abc123')

        self.assertEqual(mocked_open.call_count, 2)

    def test_http_error_with_api_payload_raises_api_error(self):
        payload = {'value': {'error': 'invalid_argument', 'message': 'bad request'}}
        fp = io.BytesIO(json.dumps(payload).encode('utf-8'))
        http_error = HTTPError(
            url='http://127.0.0.1:4723/soluna/command',
            code=400,
            msg='Bad Request',
            hdrs=None,
            fp=fp,
        )

        with patch.object(self.client._opener, 'open', side_effect=http_error):
            with self.assertRaises(SolunaAPIError) as ctx:
                self.client.execute_command('adb', ['devices'])
        self.assertEqual(ctx.exception.error_code, 'invalid_argument')
        self.assertEqual(ctx.exception.status_code, 400)

    def test_http_error_without_api_payload_raises_http_error(self):
        fp = io.BytesIO(b'plain text failure')
        http_error = HTTPError(
            url='http://127.0.0.1:4723/soluna/devices',
            code=500,
            msg='Internal Server Error',
            hdrs=None,
            fp=fp,
        )

        with patch.object(self.client._opener, 'open', side_effect=http_error):
            with self.assertRaises(SolunaHTTPError) as ctx:
                self.client.list_devices()
        self.assertEqual(ctx.exception.status_code, 500)

    def test_url_error_raises_client_error(self):
        with patch.object(self.client._opener, 'open', side_effect=URLError('connection refused')):
            with self.assertRaises(SolunaClientError):
                self.client.list_devices()

    @patch('soluna_client.request.build_opener')
    def test_client_disables_proxy_by_default(self, mock_build_opener):
        fake_opener = MagicMock()
        mock_build_opener.return_value = fake_opener

        client = SolunaClient('http://127.0.0.1:4723')

        self.assertIs(client._opener, fake_opener)
        self.assertEqual(mock_build_opener.call_count, 1)
        handlers = mock_build_opener.call_args[0]
        self.assertEqual(len(handlers), 1)

    @patch('soluna_client.request.build_opener')
    def test_client_can_use_env_proxy_when_enabled(self, mock_build_opener):
        fake_opener = MagicMock()
        mock_build_opener.return_value = fake_opener

        client = SolunaClient('http://127.0.0.1:4723', use_env_proxy=True)

        self.assertIs(client._opener, fake_opener)
        self.assertEqual(mock_build_opener.call_count, 1)
        self.assertEqual(mock_build_opener.call_args[0], ())

    def test_502_error_has_proxy_hint(self):
        fp = io.BytesIO(b'')
        http_error = HTTPError(
            url='http://127.0.0.1:4723/soluna/devices',
            code=502,
            msg='Bad Gateway',
            hdrs=None,
            fp=fp,
        )

        client = SolunaClient('http://127.0.0.1:4723', use_env_proxy=True)
        with patch.object(client._opener, 'open', side_effect=http_error):
            with self.assertRaises(SolunaHTTPError) as ctx:
                client.list_devices()
        self.assertEqual(ctx.exception.status_code, 502)
        self.assertIn('proxy', str(ctx.exception).lower())

    def test_execute_command_validates_tool(self):
        with self.assertRaises(ValueError):
            self.client.execute_command('bash', ['-lc', 'echo hi'])

    def test_get_device_validates_udid(self):
        with self.assertRaises(ValueError):
            self.client.get_device('   ')

    def test_create_log_session_validates_udid(self):
        with self.assertRaises(ValueError):
            self.client.create_log_session('   ')

    def test_read_log_session_validates_session_id(self):
        with self.assertRaises(ValueError):
            self.client.read_log_session('   ')

    def test_delete_log_session_validates_session_id(self):
        with self.assertRaises(ValueError):
            self.client.delete_log_session('   ')


if __name__ == '__main__':
    unittest.main()
