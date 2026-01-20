
"""
Pytest configuration for unit tests.

This module sets up test fixtures and mocks required dependencies
before any test modules are loaded.
"""

import sys
import types
from unittest.mock import Mock, MagicMock


# Create a comprehensive mock for torch module
def create_torch_mock():
    """Create a comprehensive mock for the torch module."""
    torch_mock = types.ModuleType('torch')
    torch_mock.__spec__ = types.SimpleNamespace(
        name='torch',
        loader=None,
        origin=None,
        submodule_search_locations=None
    )
    torch_mock.__version__ = '2.0.0'
    torch_mock.__file__ = '/mock/torch/__init__.py'
    
    # Mock torch.cuda
    cuda_mock = types.ModuleType('torch.cuda')
    cuda_mock.__spec__ = types.SimpleNamespace(
        name='torch.cuda',
        loader=None,
        origin=None,
        submodule_search_locations=None
    )
    cuda_mock.is_available = Mock(return_value=False)
    cuda_mock.empty_cache = Mock()
    cuda_mock.device_count = Mock(return_value=0)
    
    # Mock torch.utils for GLiNER
    utils_mock = types.ModuleType('torch.utils')
    utils_mock.__spec__ = types.SimpleNamespace(
        name='torch.utils',
        loader=None,
        origin=None,
        submodule_search_locations=None
    )
    
    # Mock torch.utils.data
    data_mock = types.ModuleType('torch.utils.data')
    data_mock.__spec__ = types.SimpleNamespace(
        name='torch.utils.data',
        loader=None,
        origin=None,
        submodule_search_locations=None
    )
    data_mock.DataLoader = Mock()
    
    utils_mock.data = data_mock
    
    torch_mock.cuda = cuda_mock
    torch_mock.utils = utils_mock
    torch_mock.no_grad = Mock(return_value=MagicMock(__enter__=Mock(), __exit__=Mock()))
    torch_mock.device = Mock()
    torch_mock.float16 = 'float16'
    torch_mock.float32 = 'float32'
    torch_mock.Tensor = Mock()
    
    return torch_mock, cuda_mock, utils_mock, data_mock


# Mock torch before any imports
if 'torch' not in sys.modules:
    torch_mock, cuda_mock, utils_mock, data_mock = create_torch_mock()
    sys.modules['torch'] = torch_mock
    sys.modules['torch.cuda'] = cuda_mock
    sys.modules['torch.utils'] = utils_mock
    sys.modules['torch.utils.data'] = data_mock


# Mock transformers components
def create_transformers_mock():
    """Create mock for transformers module."""
    transformers_mock = types.ModuleType('transformers')
    transformers_mock.__spec__ = types.SimpleNamespace(
        name='transformers',
        loader=None,
        origin=None,
        submodule_search_locations=None
    )
    transformers_mock.AutoTokenizer = Mock()
    transformers_mock.AutoModelForTokenClassification = Mock()
    transformers_mock.pipeline = Mock()
    
    return transformers_mock


if 'transformers' not in sys.modules:
    transformers_mock = create_transformers_mock()
    sys.modules['transformers'] = transformers_mock


# Mock GLiNER to prevent import errors
def create_gliner_mock():
    """Create mock for gliner module."""
    gliner_mock = types.ModuleType('gliner')
    gliner_mock.__spec__ = types.SimpleNamespace(
        name='gliner',
        loader=None,
        origin=None,
        submodule_search_locations=None
    )
    gliner_mock.GLiNER = Mock()
    
    # Mock gliner.model
    model_mock = types.ModuleType('gliner.model')
    model_mock.__spec__ = types.SimpleNamespace(
        name='gliner.model',
        loader=None,
        origin=None,
        submodule_search_locations=None
    )
    model_mock.GLiNER = Mock()
    
    return gliner_mock, model_mock


if 'gliner' not in sys.modules:
    gliner_mock, gliner_model_mock = create_gliner_mock()
    sys.modules['gliner'] = gliner_mock
    sys.modules['gliner.model'] = gliner_model_mock
